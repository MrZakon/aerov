package net.aero.visuals.alt;

import client.api.Theme;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.aero.visuals.localization.LocalText;
import net.aero.visuals.notification.NotificationManager;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.CharacterEvent;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MultiAccountView extends Screen {
    private static final Identifier FRAME = Identifier.fromNamespaceAndPath("aerovisuals", "multi_account_live");
    private static final RenderPipeline LIVE_FRAME_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("aerovisuals", "pipeline/multi_account_live"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader(Identifier.fromNamespaceAndPath("aerovisuals", "core/multi_account_live"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(java.util.Optional.empty())
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();
    private static volatile boolean active;
    private final Screen parent;
    private final MultiAccountSessions sessions;
    private final MultiAccountSessions.Account account;
    private final Path directory;
    private final int viewId;
    private final boolean temporary;
    private MultiAccountSessions.ViewSize viewSize;
    private int frameWidth, frameHeight;
    private MultiAccountFeed.Frames frames;
    private MultiAccountFeed.Inputs inputs;
    private NativeImage image;
    private DynamicTexture texture;
    private boolean pointerCaptured;
    private boolean initializedPointer;
    private boolean showingFrame;
    private boolean wasFocused;
    private long lastFrameTime = System.nanoTime();
    private double previousX, previousY;
    private int viewX, viewY, viewWidth, viewHeight;

    MultiAccountView(Screen parent, MultiAccountSessions sessions, MultiAccountSessions.Account account, int viewId, boolean temporary, MultiAccountSessions.ViewSize viewSize) {
        super(Component.literal(account.name));
        this.parent = parent;
        this.sessions = sessions;
        this.account = account;
        this.directory = account.directory;
        this.viewId = viewId;
        this.temporary = temporary;
        this.viewSize = viewSize;
    }

    Screen parent() { return parent; }

    public static boolean active() { return active; }

    public static int frameLimit(Minecraft client) {
        if (client == null || !(client.gui.screen() instanceof MultiAccountView view)) return 0;
        if (!client.isWindowActive() || client.getWindow().isIconified()) return 0;
        return view.viewSize.fps();
    }

    public static boolean routeKey(int key, int scanCode, int modifiers, int action, int logicalKey) {
        var client = Minecraft.getInstance();
        if (!(client.gui.screen() instanceof MultiAccountView view)) return false;
        if (!view.sessions.canControl()) {
            view.onClose();
            return true;
        }
        if (logicalKey == 298) {
            if (action == 1) {
                if (view.temporary && view.parent instanceof MultiAccountScreen) view.onClose();
                else view.sessions.openMenu(client, null, view.account);
            }
            return true;
        }
        if (logicalKey == 256 && view.temporary) {
            if (action == 1) view.onClose();
            return true;
        }
        if (logicalKey != 256 && logicalKey == view.sessions.cycleKey()) {
            if (action == 1 && !view.sessions.playNext(client)) view.onClose();
            return true;
        }
        if (logicalKey == 300) return false; // Fullscreen belongs to the main window.
        view.send(new MultiAccountFeed.Input(MultiAccountFeed.Input.KEY, key, action, modifiers, scanCode, 0));
        return true;
    }

    @Override protected void init() {
        active = true;
        viewX = viewY = 0;
        viewWidth = Math.max(1, width);
        viewHeight = Math.max(1, height);
        if (inputs == null) {
            try { inputs = new MultiAccountFeed.Inputs(directory, viewId, true); }
            catch (IOException failure) { return; }
        }
    }

    @Override public void tick() {
        super.tick();
        if (!sessions.canControl()) {
            onClose();
            return;
        }
        var currentSize = MultiAccountSessions.viewSize(minecraft, temporary);
        if (!currentSize.equals(viewSize) && sessions.resizeView(account, directory, viewId, currentSize)) {
            viewSize = currentSize;
        }
        if (!account.live() || account.stopping || !directory.equals(account.directory) || inputs == null) {
            onClose();
            return;
        }
        boolean focused = minecraft.isWindowActive();
        long now = System.nanoTime();
        if (!focused || !wasFocused) lastFrameTime = now;
        if (now - lastFrameTime >= 10_000_000_000L) {
            NotificationManager.post(LocalText.pick("Нет изображения от фонового аккаунта",
                    "No video from background account", "Немає зображення від фонового акаунта"), false);
            onClose();
            return;
        }
        if (wasFocused && !focused) {
            if (pointerCaptured) MultiAccountFocus.capturePointer(minecraft, false);
            pointerCaptured = false;
            initializedPointer = false;
            send(new MultiAccountFeed.Input(MultiAccountFeed.Input.RESET, 0, 0, 0, 0, 0));
        }
        wasFocused = focused;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        updateFrame();
        if (showingFrame) {
            context.blit(LIVE_FRAME_PIPELINE, FRAME, viewX, viewY, 0, 0,
                    viewWidth, viewHeight, frameWidth, frameHeight, frameWidth, frameHeight);
        } else {
            context.fill(0, 0, width, height, 0xFF090B0E);
            String status = LocalText.pick("Ожидаем изображение…", "Waiting for video…", "Очікуємо зображення…");
            context.centeredText(font, status, width / 2, height / 2, Theme.foreground());
        }
        String hint = temporary ? "Esc / F9" : "F9";
        String accountLabel = account.name + "  ·  " + hint;
        UiRenderer.roundedRect(context, viewX + 6, viewY + 6, font.width(accountLabel) + 12, 21, 5, 0xC0181B22);
        context.text(font, accountLabel, viewX + 12, viewY + 12, 0xFFECF0F9, false);
    }

    private void updateFrame() {
        try {
            if (frames == null) frames = MultiAccountFeed.Frames.reader(directory);
            var frame = frames.latest(viewId);
            if (frame == null) return;
            boolean allocate = image == null || frame.width() != frameWidth || frame.height() != frameHeight;
            NativeImage candidate = allocate ? new NativeImage(frame.width(), frame.height(), false) : image;
            if (!frames.read(frame, candidate.getPointer())) {
                if (allocate) candidate.close();
                return;
            }
            if (allocate) {
                DynamicTexture replacement = null;
                try {
                    replacement = new DynamicTexture(() -> "AeroVisuals account view", candidate);
                    minecraft.getTextureManager().register(FRAME, replacement);
                } catch (RuntimeException | LinkageError failure) {
                    if (replacement == null) candidate.close();
                    else replacement.close();
                    net.aero.visuals.AeroVisuals.LOGGER.warn("Could not allocate the account view texture", failure);
                    onClose();
                    return;
                }
                texture = replacement;
                image = candidate;
                frameWidth = frame.width();
                frameHeight = frame.height();
            } else {
                texture.upload();
            }
            showingFrame = true;
            lastFrameTime = System.nanoTime();
            boolean locked = frame.locked() && minecraft.isWindowActive();
            if (locked != pointerCaptured) {
                MultiAccountFocus.capturePointer(minecraft, locked);
                pointerCaptured = locked;
                initializedPointer = false;
            }
        } catch (IOException failure) {
            // The background client can be closing while this frame is being read.
        } catch (RuntimeException | LinkageError failure) {
            net.aero.visuals.AeroVisuals.LOGGER.warn("Could not update the account view texture", failure);
            onClose();
        }
    }

    @Override public void mouseMoved(double x, double y) {
        if (pointerCaptured) {
            if (initializedPointer) {
                double scale = minecraft.getWindow().getScreenWidth() / Math.max(1.0, width);
                send(new MultiAccountFeed.Input(MultiAccountFeed.Input.MOVE, 1, 0, 0,
                        (x - previousX) * scale, (y - previousY) * scale));
            }
            previousX = x;
            previousY = y;
            initializedPointer = true;
        } else {
            point(x, y);
        }
    }

    private void point(double x, double y) {
        send(new MultiAccountFeed.Input(MultiAccountFeed.Input.MOVE, 0, 0, 0,
                Math.clamp((x - viewX) / viewWidth, 0, .9999), Math.clamp((y - viewY) / viewHeight, 0, .9999)));
    }

    @Override public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        mouseButton(click, 1);
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent click) {
        mouseButton(click, 0);
        return true;
    }

    private void mouseButton(MouseButtonEvent click, int action) {
        if (!pointerCaptured) {
            if (action != 0 && (click.x() < viewX || click.x() >= viewX + viewWidth
                    || click.y() < viewY || click.y() >= viewY + viewHeight)) return;
            point(click.x(), click.y());
        }
        send(new MultiAccountFeed.Input(MultiAccountFeed.Input.BUTTON, click.button(), action, click.modifiers(), 0, 0));
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        send(new MultiAccountFeed.Input(MultiAccountFeed.Input.SCROLL, 0, 0, 0, horizontal, vertical));
        return true;
    }

    @Override public boolean charTyped(CharacterEvent input) {
        send(new MultiAccountFeed.Input(MultiAccountFeed.Input.CHARACTER, input.codepoint(), 0, 0, 0, 0));
        return true;
    }

    private void send(MultiAccountFeed.Input event) {
        if (inputs == null) return;
        if (event.type() != MultiAccountFeed.Input.RESET
                && (!sessions.canControl() || !showingFrame || !minecraft.isWindowActive())) return;
        try { inputs.send(event); }
        catch (IOException failure) { onClose(); }
    }

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {}
    @Override public boolean isPauseScreen() { return false; }

    @Override public void removed() {
        active = false;
        sessions.endView(account, directory, viewId);
        if (pointerCaptured) MultiAccountFocus.capturePointer(minecraft, false);
        pointerCaptured = false;
        if (inputs != null) {
            try (var connection = inputs) {
                connection.send(new MultiAccountFeed.Input(MultiAccountFeed.Input.RESET, 0, 0, 0, 0, 0));
            } catch (IOException ignored) {}
            inputs = null;
            try { Files.deleteIfExists(MultiAccountFeed.Inputs.fileFor(directory, viewId)); }
            catch (IOException ignored) {}
        }
        if (texture != null) minecraft.getTextureManager().release(FRAME);
        texture = null;
        image = null;
        if (frames != null) frames.close();
        frames = null;
    }

    @Override public void onClose() { minecraft.gui.setScreen(temporary ? parent : null); }
}
