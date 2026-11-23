package net.aero.visuals.alt;

import net.aero.visuals.AeroVisuals;
import net.aero.visuals.mixin.MultiAccountKeyboardInvoker;
import net.aero.visuals.mixin.MultiAccountMouseInvoker;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.input.CharInput;
import net.aero.visuals.alt.MultiAccountFeed.Input;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.Registries;

import com.mojang.blaze3d.buffers.GpuBuffer;
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
            if (setup == null || setup.server == null || !ServerAddress.isValid(setup.server)) {
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
            ClientLifecycleEvents.CLIENT_STARTED.register(MinecraftClient::scheduleStop);
        }
    }

    public static boolean managesClient() {
        return instance != null;
    }

    public static boolean requested() { return REQUESTED; }

    public static int frameLimit() { return instance == null ? 0 : instance.activeFps; }

    public static void refreshMasterVolume() {
        if (instance == null) return;
        var client = MinecraftClient.getInstance();
        client.getSoundManager().setVolume(net.minecraft.sound.SoundCategory.MASTER, instance.quiet ? 0.0f : client.options.getSoundVolumeOption(net.minecraft.sound.SoundCategory.MASTER).getValue().floatValue());
    }

    public static boolean remoteKeyDown(int key) {
        return receivingInput() && instance.heldKeys.contains(key);
    }

    public static boolean remoteButtonDown(int button) {
        return receivingInput() && instance.heldButtons.contains(button);
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

    public static void pumpInput(MinecraftClient client) {
        var worker = instance;
        if (worker != null) worker.drainInput(client);
    }

    private void drainInput(MinecraftClient client) {
        if (remoteInput != null) {
            try { readInput(client); }
            catch (IOException | RuntimeException failure) {
                state.detail = failure.getMessage();
                returnToBackground(client);
            }
        }
    }

    private void tick(MinecraftClient client) {
        long now = System.nanoTime();
        drainInput(client);
        if (now < nextPoll) return;
        nextPoll = now + 250_000_000L;
        if (!parent.isAlive()) {
            client.scheduleStop();
            return;
        }

        try {
            boolean background = activeView == 0;
            if (quiet != background) {
                quiet = background;
                refreshMasterVolume();
            }
            applyCommands(client);
            if (!connectStarted && client.getOverlay() == null && (client.currentScreen instanceof TitleScreen || client.currentScreen instanceof net.aero.visuals.gui.AeroTitleScreen || "disconnected".equals(state.phase))) {
                connectStarted = true;
                state.phase = "connecting";
                var info = new ServerInfo(setup.serverName, setup.server, ServerInfo.ServerType.OTHER);
                if (setup.resourcePack != null) {
                    info.setResourcePackPolicy(ServerInfo.ResourcePackPolicy.valueOf(setup.resourcePack));
                }
                ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(setup.server), info, false, null);
            } else if (client.currentScreen instanceof DisconnectedScreen screen) {
                state.phase = "disconnected";
                state.detail = screen.getNarratedTitle().getString();
            }
            var options = MultiAccountFiles.read(directory.resolve("options.json"), MultiAccountFiles.Request.class);
            previewEnabled = options != null && options.preview;
            updateState(client, options);
        } catch (IOException | RuntimeException failure) {
            state.detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        }
        publish();
    }

    private void applyCommands(MinecraftClient client) throws IOException {
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
                case "stop" -> client.scheduleStop();
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
                    var handler = client.getNetworkHandler();
                    if (handler == null || request.message == null || request.message.isBlank()) continue;
                    if (request.message.startsWith("/") && request.message.length() > 1) {
                        handler.sendChatCommand(request.message.substring(1));
                    } else {
                        handler.sendChatMessage(request.message);
                    }
                }
                default -> { }
            }
        }
    }

    private void updateState(MinecraftClient client, MultiAccountFiles.Request options) {
        var player = client.player;
        var handler = client.getNetworkHandler();
        state.chat = List.copyOf(chat);
        state.previewId = previewRevision;
        if (player == null || handler == null || client.world == null) return;

        state.health = player.getHealth();
        state.maxHealth = player.getMaxHealth();
        state.food = player.getHungerManager().getFoodLevel();
        state.world = client.world.getRegistryKey().getValue().toString();
        state.mode = client.interactionManager == null ? "" : client.interactionManager.getCurrentGameMode().asString();
        var entry = handler.getPlayerListEntry(player.getUuid());
        state.latency = entry == null ? -1 : entry.getLatency();
        state.dead = client.currentScreen instanceof DeathScreen;
        if (!state.dead) requestedRespawn = false;
        if (options != null && options.respawn && state.dead && activeView == 0 && !requestedRespawn) {
            requestedRespawn = true;
            player.requestRespawn();
        }

        var hotbar = new ArrayList<MultiAccountFiles.HotbarItem>(9);
        for (int slot = 0; slot < 9; slot++) {
            var stack = player.getInventory().getStack(slot);
            hotbar.add(new MultiAccountFiles.HotbarItem(Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount()));
        }
        state.hotbar = hotbar;
    }

    private void readInput(MinecraftClient client) throws IOException {
        long window = client.getWindow().getHandle();
        var keys = (MultiAccountKeyboardInvoker) (Object) client.keyboard;
        var buttons = (MultiAccountMouseInvoker) (Object) client.mouse;
        var pointer = (MultiAccountPointer) (Object) client.mouse;
        for (int i = 0; i < 256; i++) {
            Input input = remoteInput.read();
            if (input == null) return;
            if (input.type() == Input.RESET) {
                resetInput(client);
                continue;
            }

            switch (input.type()) {
                case Input.KEY -> {
                    int logicalKey = input.first();
                    if (input.second() == 0) heldKeys.remove(logicalKey);
                    else heldKeys.add(logicalKey);
                    keys.aero$sendKey(window, input.second(), new KeyInput(input.first(), (int) input.x(), input.modifiers()));
                }
                case Input.CHARACTER -> keys.aero$sendCharacter(window, new CharInput(input.first(), input.modifiers()));
                case Input.MOVE -> {
                    if (input.first() == 1) pointer.aero$look(input.x(), input.y());
                    else pointer.aero$point(input.x(), input.y());
                }
                case Input.BUTTON -> {
                    if (input.second() == 0) heldButtons.remove(input.first());
                    else heldButtons.add(input.first());
                    buttons.aero$sendButton(window, new MouseInput(input.first(), input.modifiers()), input.second());
                }
                case Input.SCROLL -> buttons.aero$sendScroll(window, input.x(), input.y());
                default -> { }
            }
        }
    }

    private void returnToBackground(MinecraftClient client) {
        endControl(client);
        client.options.getMaxFps().setValue(MultiAccountBudget.IDLE_FPS);
        releaseIdleReadbacks();
        MultiAccountFocus.resize(client, MultiAccountFocus.BACKGROUND_WIDTH, MultiAccountFocus.BACKGROUND_HEIGHT);
    }

    private void releaseIdleReadbacks() {
        for (Readback readback : readbacks) {
            if (!readback.pending) readback.close();
        }
    }

    private void endControl(MinecraftClient client) {
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

    private void resetInput(MinecraftClient client) {
        ((MultiAccountPointer) (Object) client.mouse).aero$resetMotion();
        var buttons = (MultiAccountMouseInvoker) (Object) client.mouse;
        for (int button : heldButtons) {
            buttons.aero$sendButton(client.getWindow().getHandle(), new MouseInput(button, 0), 0);
        }
        heldButtons.clear();
        heldKeys.clear();
        net.minecraft.client.option.KeyBinding.unpressAll();
    }

    private void changeViewSize(MinecraftClient client, MultiAccountFiles.Request request) {
        if (request.width < 1 || request.height < 1 || request.width > MultiAccountFeed.MAX_DIMENSION
                || request.height > MultiAccountFeed.MAX_DIMENSION) return;
        if (4L * request.width * request.height > MultiAccountFeed.MAX_PIXEL_BYTES) return;
        activeFps = Math.clamp(request.fps, 10, MultiAccountBudget.UNLIMITED_FPS);
        client.options.getMaxFps().setValue(activeFps);
        client.options.getEnableVsync().setValue(false);
        MultiAccountFocus.resize(client, request.width, request.height);
    }

    public static boolean planFrame(MinecraftClient client) {
        var worker = instance;
        if (worker == null) return false;
        worker.skipFrame = worker.activeView == 0 && client.world != null && client.player != null
                && client.getOverlay() == null && client.currentScreen == null
                && (!worker.previewEnabled || worker.previewPending || System.nanoTime() < worker.nextPreviewFrame);
        return worker.skipFrame;
    }

    public static void renderedFrame(MinecraftClient client) {
        var worker = instance;
        if (worker != null && !worker.skipFrame) worker.captureFrame(client);
    }

    private void captureFrame(MinecraftClient client) {
        var target = client.getFramebuffer();
        if (target == null) return;
        var source = target.getColorAttachment();
        if (source == null || closing) return;
        int viewId = activeView;
        if (viewId != 0) {
            boolean locked = client.currentScreen == null && client.player != null && client.mouse.isCursorLocked();
            captureLive(source, viewId, locked);
            return;
        }

        long now = System.nanoTime();
        if (!previewEnabled || previewPending || now < nextPreviewFrame) return;
        previewPending = true;
        nextPreviewFrame = now + 330_000_000L;
        try {
            ScreenshotRecorder.takeScreenshot(target, screenshot ->
                    CompletableFuture.runAsync(() -> savePreview(screenshot)).whenComplete((ignored, error) ->
                            client.execute(() -> previewPending = false)));
        } catch (RuntimeException failure) {
            previewPending = false;
            AeroVisuals.LOGGER.debug("Could not capture background account preview", failure);
        }
    }

    private void captureLive(com.mojang.blaze3d.textures.GpuTexture source, int viewId, boolean locked) {
        int width = source.getWidth(0);
        int height = source.getHeight(0);
        long bytes = 4L * width * height;
        if (width > MultiAccountFeed.MAX_DIMENSION || height > MultiAccountFeed.MAX_DIMENSION
                || bytes <= 0 || bytes > MultiAccountFeed.MAX_PIXEL_BYTES || source.getFormat().pixelSize() != 4) return;
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
                    try (var mapped = device.createCommandEncoder().mapBuffer(buffer, true, false)) {
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
            MultiAccountFiles.write(directory.resolve("preview.png"), image::writeTo);
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
