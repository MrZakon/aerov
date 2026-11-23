package net.aero.visuals.hud;

import net.aero.visuals.ClientRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

public final class HudDragController {
    private static DraggableHud dragged;
    private static ChatScreen dragScreen;
    private static Object dragWorld;
    private static Object dragPlayer;
    private static boolean movedPastClick;
    private static int dragScreenWidth, dragScreenHeight;
    private static double offsetX, offsetY, dragX, dragY, initialX, initialY, dragWidth, dragHeight;

    private HudDragController() {}

    public static double mouseX() {
        var client = Minecraft.getInstance();
        return client.mouseHandler.xpos() * client.getWindow().getGuiScaledWidth() / Math.max(1.0, client.getWindow().getScreenWidth());
    }

    public static double mouseY() {
        var client = Minecraft.getInstance();
        return client.mouseHandler.ypos() * client.getWindow().getGuiScaledHeight() / Math.max(1.0, client.getWindow().getScreenHeight());
    }

    public static boolean mouseButton(int button, int action) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        var client = Minecraft.getInstance();
        if (action == GLFW.GLFW_RELEASE && dragged != null) {
            if (!validDrag(client)) {
                clearDrag();
                return false;
            }
            var released = dragged;
            double mouseX = mouseX(), mouseY = mouseY();
            noteMotion(mouseX, mouseY);
            boolean click = !movedPastClick;
            finish();
            if (click) {
                var layout = chatLayout(client, client.gui.screen());
                if (layout != null && layout.hit(mouseX, mouseY) == released) released.onHudClick(mouseX, mouseY);
            }
            return true;
        }
        if (action != GLFW.GLFW_PRESS || !(client.gui.screen() instanceof ChatScreen chat)
                || !client.isWindowActive() || client.player == null || client.level == null
                || net.aero.visuals.render.ClientView.hud().isHidden()) return false;
        var layout = chatLayout(client, chat);
        if (layout == null) return false;
        double mouseX = mouseX(), mouseY = mouseY();
        var widget = layout.hit(mouseX, mouseY);
        if (widget == null) return false;
        var bounds = layout.bounds(widget);
        dragged = widget;
        dragScreen = chat;
        dragWorld = client.level;
        dragPlayer = client.player;
        dragScreenWidth = client.getWindow().getGuiScaledWidth();
        dragScreenHeight = client.getWindow().getGuiScaledHeight();
        movedPastClick = false;
        dragX = initialX = bounds.x();
        dragY = initialY = bounds.y();
        dragWidth = bounds.width();
        dragHeight = bounds.height();
        offsetX = mouseX - bounds.x();
        offsetY = mouseY - bounds.y();
        return true;
    }

    private static HudLayout chatLayout(Minecraft client, Object screen) {
        var runtime = ClientRuntime.getNullable();
        if (runtime == null) return null;
        var layout = runtime.hudLayout();
        var window = client.getWindow();
        return layout.matches(screen, client.player, client.level, window.getGuiScaledWidth(), window.getGuiScaledHeight()) ? layout : null;
    }

    private static boolean validDrag(Minecraft client) {
        return dragged != null && client.isWindowActive() && client.gui.screen() == dragScreen
                && client.player != null && client.player == dragPlayer && client.level == dragWorld && !net.aero.visuals.render.ClientView.hud().isHidden()
                && client.getWindow().getGuiScaledWidth() == dragScreenWidth && client.getWindow().getGuiScaledHeight() == dragScreenHeight
                && net.aero.visuals.guard.AeroGuard.allowed() && dragged.isVisible();
    }

    private static void clearDrag() {
        dragged = null;
        dragScreen = null;
        dragWorld = null;
        dragPlayer = null;
        movedPastClick = false;
    }

    private static void noteMotion(double x, double y) {
        double dx = x - initialX - offsetX, dy = y - initialY - offsetY;
        movedPastClick |= dx * dx + dy * dy > 9;
    }

    public static void pointerMoved(long window) {
        if (dragged == null || movedPastClick || window != Minecraft.getInstance().getWindow().handle()) return;
        noteMotion(mouseX(), mouseY());
    }

    public static void tick(Minecraft client) {
        if (dragged != null && !validDrag(client)) clearDrag();
    }

    public static void update() {
        if (dragged == null) return;
        var client = Minecraft.getInstance();
        tick(client);
        if (dragged == null) return;
        if (GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            finish();
            return;
        }
        double mouseX = mouseX(), mouseY = mouseY();
        noteMotion(mouseX, mouseY);
        if (!movedPastClick || !dragged.isDraggable()) return;
        moveWidget(client, mouseX, mouseY);
    }

    private static void moveWidget(Minecraft client, double mouseX, double mouseY) {
        dragX = Math.clamp(mouseX - offsetX, 0, Math.max(0, client.getWindow().getGuiScaledWidth() - dragWidth));
        dragY = Math.clamp(mouseY - offsetY, 0, Math.max(0, client.getWindow().getGuiScaledHeight() - dragHeight));
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
        var client = Minecraft.getInstance();
        if (!validDrag(client) || !widget.isDraggable()) {
            clearDrag();
            return;
        }
        double mouseX = mouseX(), mouseY = mouseY();
        noteMotion(mouseX, mouseY);
        if (!movedPastClick) {
            clearDrag();
            return;
        }
        moveWidget(client, mouseX, mouseY);
        double x = dragX, y = dragY, width = dragWidth, height = dragHeight;
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        boolean moved = Math.abs(x - initialX) > 0.01 || Math.abs(y - initialY) > 0.01;
        clearDrag();
        var runtime = ClientRuntime.getNullable();
        if (moved && runtime != null && runtime.config() != null) {
            var saved = runtime.config().hudPosition(widget.id(), null);
            var anchor = saved == null ? widget.defaultHudAnchor() : saved.anchor();
            double scale = saved == null ? 1 : saved.scale();
            runtime.config().setHudPosition(widget.id(),
                    HudPosition.fromScreen(anchor, x, y, width, height, screenWidth, screenHeight, scale));
        }
    }

    public static void closeChat() {
        try {
            finish();
        } finally {
            var runtime = ClientRuntime.getNullable();
            if (runtime != null) runtime.hudLayout().clear();
        }
    }

    public static void renderChat(GuiGraphicsExtractor context) {
        update();
        var runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        var client = Minecraft.getInstance();
        var window = client.getWindow();
        var layout = runtime.hudLayout();
        layout.begin(client.gui.screen(), client.player, client.level, window.getGuiScaledWidth(), window.getGuiScaledHeight());
        boolean complete = false;
        try {
            for (var module : runtime.modules().all()) {
                if (module instanceof DraggableHud widget && widget.isEnabled()) widget.draw(context);
            }
            complete = true;
        } finally {
            layout.finish(complete);
        }
    }
}
