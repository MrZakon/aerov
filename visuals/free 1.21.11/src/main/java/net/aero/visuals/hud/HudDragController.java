package net.aero.visuals.hud;

import net.aero.visuals.ClientRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;

public final class HudDragController {
    private static DraggableHud dragged;
    private static ChatScreen dragScreen;
    private static Object dragWorld;
    private static boolean movedPastClick;
    private static double offsetX, offsetY, dragX, dragY, initialX, initialY, dragWidth, dragHeight;

    private HudDragController() {}

    public static double mouseX() {
        var client = MinecraftClient.getInstance();
        return client.mouse.getX() * client.getWindow().getScaledWidth() / Math.max(1.0, client.getWindow().getWidth());
    }

    public static double mouseY() {
        var client = MinecraftClient.getInstance();
        return client.mouse.getY() * client.getWindow().getScaledHeight() / Math.max(1.0, client.getWindow().getHeight());
    }

    public static boolean mouseButton(int button, int action) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        var client = MinecraftClient.getInstance();
        if (action == GLFW.GLFW_RELEASE && dragged != null) {
            if (!validDrag(client)) {
                clearDrag();
                return false;
            }
            var released = dragged;
            double mouseX = mouseX(), mouseY = mouseY();
            noteMotion(mouseX, mouseY);
            boolean click = !movedPastClick;
            if (!click && released.isDraggable()) moveWidget(client, mouseX, mouseY);
            finish();
            if (click && released.isVisible()) released.onHudClick(mouseX, mouseY);
            return true;
        }
        var runtime = ClientRuntime.getNullable();
        if (action != GLFW.GLFW_PRESS || !(client.currentScreen instanceof ChatScreen chat)
                || !client.isWindowFocused() || client.player == null || client.world == null
                || client.options.hudHidden || runtime == null) return false;
        double mouseX = mouseX(), mouseY = mouseY();
        var modules = runtime.modules().all();
        for (int index = modules.size() - 1; index >= 0; index--) {
            if (!(modules.get(index) instanceof DraggableHud widget) || !widget.isVisible()) continue;
            HudBounds bounds = runtime.hudLayout().bounds().get(widget.id());
            if (bounds == null || !bounds.contains(mouseX, mouseY)) continue;
            dragged = widget;
            dragScreen = chat;
            dragWorld = client.world;
            movedPastClick = false;
            dragX = initialX = bounds.x();
            dragY = initialY = bounds.y();
            dragWidth = bounds.width(); dragHeight = bounds.height();
            offsetX = mouseX - bounds.x(); offsetY = mouseY - bounds.y();
            return true;
        }
        return false;
    }

    private static boolean validDrag(MinecraftClient client) {
        return dragged != null && client.isWindowFocused() && client.currentScreen == dragScreen
                && client.player != null && client.world == dragWorld && !client.options.hudHidden
                && net.aero.visuals.guard.AeroGuard.allowed() && dragged.isVisible();
    }

    private static void clearDrag() {
        dragged = null;
        dragScreen = null;
        dragWorld = null;
        movedPastClick = false;
    }

    private static void noteMotion(double x, double y) {
        double dx = x - initialX - offsetX, dy = y - initialY - offsetY;
        movedPastClick |= dx * dx + dy * dy > 9;
    }

    public static void pointerMoved(long window) {
        if (dragged == null || movedPastClick || window != MinecraftClient.getInstance().getWindow().getHandle()) return;
        noteMotion(mouseX(), mouseY());
    }

    public static void tick(MinecraftClient client) {
        if (dragged != null && !validDrag(client)) clearDrag();
    }

    public static void update() {
        if (dragged == null) return;
        var client = MinecraftClient.getInstance();
        tick(client);
        if (dragged == null) return;
        if (GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            finish();
            return;
        }
        double mouseX = mouseX(), mouseY = mouseY();
        noteMotion(mouseX, mouseY);
        if (!movedPastClick || !dragged.isDraggable()) return;
        var runtime = ClientRuntime.getNullable();
        var bounds = runtime == null ? null : runtime.hudLayout().bounds().get(dragged.id());
        if (bounds != null) { dragWidth = bounds.width(); dragHeight = bounds.height(); }
        moveWidget(client, mouseX, mouseY);
    }

    private static void moveWidget(MinecraftClient client, double mouseX, double mouseY) {
        dragX = Math.clamp(mouseX - offsetX, 0, Math.max(0, client.getWindow().getScaledWidth() - dragWidth));
        dragY = Math.clamp(mouseY - offsetY, 0, Math.max(0, client.getWindow().getScaledHeight() - dragHeight));
    }

    public static void updateBounds(DraggableHud widget, HudBounds bounds) {
        if (dragged != widget) return;
        dragWidth = bounds.width(); dragHeight = bounds.height();
        dragX = bounds.x(); dragY = bounds.y();
    }

    public static double x(DraggableHud widget, double fallback) { return dragged == widget ? dragX : fallback; }
    public static double y(DraggableHud widget, double fallback) { return dragged == widget ? dragY : fallback; }
    public static boolean isDragging(DraggableHud widget) { return dragged == widget && widget.isDraggable(); }

    public static void finish() {
        if (dragged == null) return;
        var widget = dragged;
        var client = MinecraftClient.getInstance();
        if (!movedPastClick || !validDrag(client) || !widget.isDraggable()) {
            clearDrag();
            return;
        }
        double x = Math.clamp(dragX, 0, Math.max(0, client.getWindow().getScaledWidth() - dragWidth));
        double y = Math.clamp(dragY, 0, Math.max(0, client.getWindow().getScaledHeight() - dragHeight));
        boolean moved = Math.abs(x - initialX) > 0.01 || Math.abs(y - initialY) > 0.01;
        clearDrag();
        var runtime = ClientRuntime.getNullable();
        if (moved && runtime != null && runtime.config() != null) {
            runtime.config().setHudPosition(widget.id(), new HudPosition(HudAnchor.TOP_LEFT, x, y, 1));
        }
    }

    public static void renderChat(DrawContext context) {
        update();
        var runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        for (var module : runtime.modules().all()) {
            if (module instanceof DraggableHud widget && widget.isEnabled()) widget.draw(context);
        }
    }
}
