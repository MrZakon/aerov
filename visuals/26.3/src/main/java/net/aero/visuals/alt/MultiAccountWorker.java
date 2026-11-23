package net.aero.visuals.alt;

import client.util.LegacyInput;

import net.aero.visuals.AeroVisuals;
import net.aero.visuals.mixin.MultiAccountKeyboardInvoker;
import net.aero.visuals.mixin.MultiAccountMouseInvoker;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.CharacterEvent;
import net.aero.visuals.alt.MultiAccountFeed.Input;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.BuiltInRegistries;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MultiAccountWorker {
    private static final boolean REQUESTED = System.getProperty("aero.multi.directory") != null;
    private static MultiAccountWorker instance;

    private final Path directory;
    private final ProcessHandle parent;
    private final MultiAccountFiles.Request setup;
    private final MultiAccountFiles.State state = new MultiAccountFiles.State();
    private final ArrayDeque<String> chat = new ArrayDeque<>();
    private long nextPoll;
    private long nextPreviewFrame;
    private boolean skipFrame;
    private boolean previewEnabled;
    private int lastCommand;
    private boolean previewPending;
    private final Readback[] readbacks = { new Readback(), new Readback() };
    private long submittedFrame;
    private long publishedFrame;
    private boolean closing;
    private boolean connectStarted;
    private boolean requestedRespawn;
    private boolean quiet;
    private volatile long previewRevision;
    private MultiAccountFeed.Inputs remoteInput;
    private MultiAccountFeed.Frames liveFrames;
    private int activeView;
    private int activeFps = MultiAccountBudget.IDLE_FPS;
    private final java.util.Set<Integer> heldKeys = new java.util.HashSet<>();
    private final java.util.Set<Integer> heldButtons = new java.util.HashSet<>();

    private MultiAccountWorker(Path directory, ProcessHandle parent, MultiAccountFiles.Request setup) {
        this.directory = directory;
        this.parent = parent;
        this.setup = setup;
    }

    public static void install() {
        String path = System.getProperty("aero.multi.directory");
        long pid = Long.getLong("aero.multi.parent", -1L);
        if (path == null || pid < 1) return;
        try {
            Path directory = Path.of(path).toRealPath();
            var parent = ProcessHandle.of(pid).orElseThrow();
            var setup = MultiAccountFiles.read(directory.resolve("setup.json"), MultiAccountFiles.Request.class);
            if (setup == null || setup.server == null || !ServerAddress.isValidAddress(setup.server)) {
                throw new IOException("Missing background account server");
            }
            // The launcher has consumed this file. It may contain a temporary Minecraft session token.
            if (directory.getFileName().toString().startsWith("aerovisuals-multi-")
                    && directory.getParent().equals(Path.of(System.getProperty("java.io.tmpdir")).toRealPath())) {
                try { Files.deleteIfExists(directory.resolve("java.args")); }
                catch (IOException ignored) { }
            }
            instance = new MultiAccountWorker(directory, parent, setup);
            instance.register();
        } catch (IOException | RuntimeException failure) {
            AeroVisuals.LOGGER.warn("Could not initialize background account", failure);
            ClientLifecycleEvents.CLIENT_STARTED.register(Minecraft::stop);
        }
    }

    public static boolean managesClient() {
        return instance != null;
    }

    public static boolean requested() { return REQUESTED; }

    public static int frameLimit() { return instance == null ? 0 : instance.activeFps; }

    public static void refreshMasterVolume() {
        if (instance == null) return;
        var client = Minecraft.getInstance();
        client.getSoundManager().updateCategoryVolume(net.minecraft.sounds.SoundSource.MASTER, instance.quiet ? 0.0f : client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).get().floatValue());
    }

    public static boolean remoteKeyDown(int key) {
        return receivingInput() && instance.heldKeys.contains(key);
    }

    public static boolean remoteButtonDown(int button) {
        return receivingInput() && instance.heldButtons.contains(switch (button) { case 0 -> 1; case 1 -> 3; case 2 -> 2; default -> button + 1; });
    }

    public static boolean receivingInput() { return instance != null && instance.activeView != 0; }

    private void register() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            MultiAccountFocus.hide(client);
            client.options.pauseOnLostFocus = false;
            publish();
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientReceiveMessageEvents.CHAT.register((text, signed, sender, parameters, receivedAt) -> recordChat(text.getString()));
        ClientReceiveMessageEvents.GAME.register((text, overlay) -> {
            if (!overlay) recordChat(text.getString());
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            state.phase = "online";
            state.detail = "";
            publish();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            state.phase = "disconnected";
            publish();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            closing = true;
            endControl(client);
            releaseIdleReadbacks();
            if (!"disconnected".equals(state.phase)) state.phase = "stopped";
            publish();
        });
    }

    private void recordChat(String message) {
        if (chat.size() == 48) chat.removeFirst();
        chat.addLast(message.length() <= 512 ? message : message.substring(0, 512));
    }

    public static void pumpInput(Minecraft client) {
        var worker = instance;
        if (worker != null) worker.drainInput(client);
    }

    private void drainInput(Minecraft client) {
        if (remoteInput != null) {
            try { readInput(client); }
            catch (IOException | RuntimeException failure) {
                state.detail = failure.getMessage();
                returnToBackground(client);
            }
        }
    }

    private void tick(Minecraft client) {
        long now = System.nanoTime();
        drainInput(client);
        if (now < nextPoll) return;
        nextPoll = now + 250_000_000L;
        if (!parent.isAlive()) {
            client.stop();
            return;
        }

        try {
            boolean background = activeView == 0;
            if (quiet != background) {
                quiet = background;
                refreshMasterVolume();
            }
            applyCommands(client);
            if (!connectStarted && client.gui.overlay() == null && (client.gui.screen() instanceof TitleScreen || client.gui.screen() instanceof net.aero.visuals.gui.AeroTitleScreen || "disconnected".equals(state.phase))) {
                connectStarted = true;
                state.phase = "connecting";
                var info = new ServerData(setup.serverName, setup.server, ServerData.Type.OTHER);
                if (setup.resourcePack != null) {
                    info.setResourcePackStatus(ServerData.ServerPackStatus.valueOf(setup.resourcePack));
                }
                ConnectScreen.startConnecting(client.gui.screen(), client, ServerAddress.parseString(setup.server), info, false, null);
            } else if (client.gui.screen() instanceof DisconnectedScreen screen) {
                state.phase = "disconnected";
                state.detail = screen.getNarrationMessage().getString();
            }
            var options = MultiAccountFiles.read(directory.resolve("options.json"), MultiAccountFiles.Request.class);
            previewEnabled = options != null && options.preview;
            updateState(client, options);
        } catch (IOException | RuntimeException failure) {
            state.detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        }
        publish();
    }

    private void applyCommands(Minecraft client) throws IOException {
        for (int i = 0; i < 8; i++) {
            Path path = directory.resolve("command-" + (lastCommand + 1) + ".json");
            var request = MultiAccountFiles.read(path, MultiAccountFiles.Request.class);
            if (request == null) return;
            lastCommand++;
            Files.deleteIfExists(path);
            switch (request.action) {
                case "reconnect" -> {
                    if ("disconnected".equals(state.phase)) connectStarted = false;
                }
                case "stop" -> client.stop();
                case "view" -> {
                    if (request.viewId <= 0 || activeView == request.viewId) continue;
                    endControl(client);
                    if (liveFrames == null) liveFrames = MultiAccountFeed.Frames.writer(directory);
                    remoteInput = new MultiAccountFeed.Inputs(directory, request.viewId, false);
                    activeView = request.viewId;
                    changeViewSize(client, request);
                }
                case "resize" -> {
                    if (activeView == request.viewId) changeViewSize(client, request);
                }
                case "unview" -> {
                    if (activeView == request.viewId) {
                        returnToBackground(client);
                        var runtime = net.aero.visuals.ClientRuntime.getNullable();
                        if (runtime != null) runtime.config().flush();
                    }
                }
                case "chat" -> {
                    var handler = client.getConnection();
                    if (handler == null || request.message == null || request.message.isBlank()) continue;
                    if (request.message.startsWith("/") && request.message.length() > 1) {
                        handler.sendCommand(request.message.substring(1));
                    } else {
                        handler.sendChat(request.message);
                    }
                }
                default -> { }
            }
        }
    }

    private void updateState(Minecraft client, MultiAccountFiles.Request options) {
        var player = client.player;
        var handler = client.getConnection();
        state.chat = List.copyOf(chat);
        state.previewId = previewRevision;
        if (player == null || handler == null || client.level == null) return;

        state.health = player.getHealth();
        state.maxHealth = player.getMaxHealth();
        state.food = player.getFoodData().getFoodLevel();
        state.world = client.level.dimension().identifier().toString();
        state.mode = client.gameMode == null ? "" : client.gameMode.getPlayerMode().getName();
        var entry = handler.getPlayerInfo(player.getUUID());
        state.latency = entry == null ? -1 : entry.getLatency();
        state.dead = client.gui.screen() instanceof DeathScreen;
        if (!state.dead) requestedRespawn = false;
        if (options != null && options.respawn && state.dead && activeView == 0 && !requestedRespawn) {
            requestedRespawn = true;
            player.respawn();
        }

        var hotbar = new ArrayList<MultiAccountFiles.HotbarItem>(9);
        for (int slot = 0; slot < 9; slot++) {
            var stack = player.getInventory().getItem(slot);
            hotbar.add(new MultiAccountFiles.HotbarItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount()));
        }
        state.hotbar = hotbar;
    }

    private void readInput(Minecraft client) throws IOException {
        long window = client.getWindow().handle();
        var keys = (MultiAccountKeyboardInvoker) (Object) client.keyboardHandler;
        var buttons = (MultiAccountMouseInvoker) (Object) client.mouseHandler;
        var pointer = (MultiAccountPointer) (Object) client.mouseHandler;
        for (int i = 0; i < 256; i++) {
            Input input = remoteInput.read();
            if (input == null) return;
            if (input.type() == Input.RESET) {
                resetInput(client);
                continue;
            }

            switch (input.type()) {
                case Input.KEY -> {
                    int logicalKey = LegacyInput.key(input.first());
                    if (input.second() == 0) heldKeys.remove(logicalKey);
                    else heldKeys.add(logicalKey);
                    keys.aero$sendKey(window, input.second(), new KeyEvent(input.first(), (int) input.x(), input.modifiers()));
                }
                case Input.CHARACTER -> keys.aero$sendCharacter(window, new CharacterEvent(input.first()));
                case Input.MOVE -> {
                    if (input.first() == 1) pointer.aero$look(input.x(), input.y());
                    else pointer.aero$point(input.x(), input.y());
                }
                case Input.BUTTON -> {
                    if (input.second() == 0) heldButtons.remove(input.first());
                    else heldButtons.add(input.first());
                    buttons.aero$sendButton(window, new MouseButtonInfo(input.first(), input.modifiers()), input.second());
                }
                case Input.SCROLL -> buttons.aero$sendScroll(window, input.x(), input.y());
                default -> { }
            }
        }
    }

    private void returnToBackground(Minecraft client) {
        endControl(client);
        client.options.framerateLimit().set(MultiAccountBudget.IDLE_FPS);
        releaseIdleReadbacks();
        MultiAccountFocus.resize(client, MultiAccountFocus.BACKGROUND_WIDTH, MultiAccountFocus.BACKGROUND_HEIGHT);
    }

    private void releaseIdleReadbacks() {
        for (Readback readback : readbacks) {
            if (!readback.pending) readback.close();
        }
    }

    private void endControl(Minecraft client) {
        if (activeView == 0) return;
        int viewId = activeView;
        try {
            resetInput(client);
        } finally {
            activeView = 0;
            if (liveFrames != null) {
                liveFrames.close();
                liveFrames = null;
            }
            activeFps = MultiAccountBudget.IDLE_FPS;
            try { remoteInput.close(); } catch (IOException ignored) {}
            remoteInput = null;
            try { Files.deleteIfExists(MultiAccountFeed.Inputs.fileFor(directory, viewId)); }
            catch (IOException ignored) {}
        }
    }

    private void resetInput(Minecraft client) {
        ((MultiAccountPointer) (Object) client.mouseHandler).aero$resetMotion();
        var buttons = (MultiAccountMouseInvoker) (Object) client.mouseHandler;
        for (int button : heldButtons) {
            buttons.aero$sendButton(client.getWindow().handle(), new MouseButtonInfo(button, 0), 0);
        }
        heldButtons.clear();
        heldKeys.clear();
        net.minecraft.client.KeyMapping.releaseAll();
    }

    private void changeViewSize(Minecraft client, MultiAccountFiles.Request request) {
        if (request.width < 1 || request.height < 1 || request.width > MultiAccountFeed.MAX_DIMENSION
                || request.height > MultiAccountFeed.MAX_DIMENSION) return;
        if (4L * request.width * request.height > MultiAccountFeed.MAX_PIXEL_BYTES) return;
        activeFps = Math.clamp(request.fps, 10, MultiAccountBudget.UNLIMITED_FPS);
        client.options.framerateLimit().set(activeFps);
        client.options.enableVsync().set(false);
        MultiAccountFocus.resize(client, request.width, request.height);
    }

    public static boolean planFrame(Minecraft client) {
        var worker = instance;
        if (worker == null) return false;
        worker.skipFrame = worker.activeView == 0 && client.level != null && client.player != null
                && client.gui.overlay() == null && client.gui.screen() == null
                && (!worker.previewEnabled || worker.previewPending || System.nanoTime() < worker.nextPreviewFrame);
        return worker.skipFrame;
    }

    public static boolean skipPlannedFrame() {
        return instance != null && instance.skipFrame;
    }

    public static void renderedFrame(Minecraft client) {
        var worker = instance;
        if (worker != null && !worker.skipFrame) worker.captureFrame(client);
    }

    private void captureFrame(Minecraft client) {
        var target = client.gameRenderer.mainRenderTarget();
        if (target == null) return;
        var source = target.getColorTexture();
        if (source == null || closing) return;
        int viewId = activeView;
        if (viewId != 0) {
            boolean locked = client.gui.screen() == null && client.player != null && client.mouseHandler.isMouseGrabbed();
            captureLive(source, viewId, locked);
            return;
        }

        long now = System.nanoTime();
        if (!previewEnabled || previewPending || now < nextPreviewFrame) return;
        previewPending = true;
        nextPreviewFrame = now + 330_000_000L;
        try {
            Screenshot.takeScreenshot(target, screenshot ->
                    CompletableFuture.runAsync(() -> savePreview(screenshot)).whenComplete((ignored, error) ->
                            client.execute(() -> previewPending = false)));
        } catch (RuntimeException failure) {
            previewPending = false;
            AeroVisuals.LOGGER.debug("Could not capture background account preview", failure);
        }
    }

    private void captureLive(com.mojang.renderpearl.api.textures.GpuTexture source, int viewId, boolean locked) {
        int width = source.getWidth(0);
        int height = source.getHeight(0);
        long bytes = 4L * width * height;
        if (width > MultiAccountFeed.MAX_DIMENSION || height > MultiAccountFeed.MAX_DIMENSION
                || bytes <= 0 || bytes > MultiAccountFeed.MAX_PIXEL_BYTES || source.getFormat().blockSize() != 4) return;
        Readback selected = null;
        for (Readback candidate : readbacks) {
            if (!candidate.pending) {
                selected = candidate;
                break;
            }
        }
        if (selected == null) return;
        final Readback slot = selected;
        long frame = ++submittedFrame;
        var device = RenderSystem.getDevice();
        try {
            if (slot.buffer == null || slot.bytes != bytes) {
                slot.close();
                slot.buffer = device.createBuffer(() -> "AeroVisuals account frames",
                        GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, bytes);
                slot.bytes = bytes;
            }
            var buffer = slot.buffer;
            slot.pending = true;
            device.createCommandEncoder().copyTextureToBuffer(source, buffer, 0, () -> {
                try {
                    if (closing || activeView != viewId || frame <= publishedFrame) return;
                    try (var mapped = buffer.map(true, false)) {
                        liveFrames.publish(viewId, width, height, MemoryUtil.memAddress(mapped.data()), locked);
                        publishedFrame = frame;
                    }
                } catch (IOException | RuntimeException failure) {
                    AeroVisuals.LOGGER.debug("Could not send background account frame", failure);
                } finally {
                    slot.pending = false;
                    if (closing || activeView == 0) slot.close();
                }
            }, 0);
        } catch (RuntimeException failure) {
            slot.pending = false;
            AeroVisuals.LOGGER.debug("Could not capture background account frame", failure);
        }
    }

    private static final class Readback {
        GpuBuffer buffer;
        long bytes;
        boolean pending;

        void close() {
            if (buffer != null) {
                buffer.close();
                buffer = null;
            }
        }
    }

    private void savePreview(NativeImage screenshot) {
        int width = Math.min(480, screenshot.getWidth());
        int height = Math.max(1, screenshot.getHeight() * width / screenshot.getWidth());
        try (screenshot; NativeImage image = new NativeImage(width, height, false)) {
            screenshot.resizeSubRectTo(0, 0, screenshot.getWidth(), screenshot.getHeight(), image);
            MultiAccountFiles.write(directory.resolve("preview.png"), image::writeToFile);
            previewRevision++;
        } catch (IOException | RuntimeException failure) {
            AeroVisuals.LOGGER.debug("Could not update background account preview", failure);
        }
    }

    private void publish() {
        try {
            MultiAccountFiles.write(directory.resolve("state.json"), state);
        } catch (IOException failure) {
            AeroVisuals.LOGGER.debug("Could not write background account state", failure);
        }
    }
}
