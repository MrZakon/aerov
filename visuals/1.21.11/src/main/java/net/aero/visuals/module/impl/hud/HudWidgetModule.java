package net.aero.visuals.module.impl.hud;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.Render2DEvent;
import net.aero.visuals.hud.*;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

public abstract class HudWidgetModule extends Module implements DraggableHud {
    protected final DoubleSetting scale = add(new DoubleSetting("scale", "Масштаб", "Изменяет размер этого HUD. Для перемещения откройте чат и перетащите виджет.", 0.65, 0.5, 2, 0.05));
    protected final DoubleSetting opacity;
    protected double width = 160, height = 44;
    protected boolean content = true;
    private final HudPosition fallback;
    private HudBounds bounds;
    private double drawnScale = 1;
    private float tickDelta;

    protected HudWidgetModule(String id, String name, String description, HudPosition fallback) {
        this(id, name, description, fallback, 78);
    }

    protected HudWidgetModule(String id, String name, String description, HudPosition fallback, double defaultOpacity) {
        super(id, name, description + " Перемещается левой кнопкой мыши при открытом чате.", ModuleCategory.HUD);
        opacity = add(new DoubleSetting("opacity", "Непрозрачность фона (%)", "Изменяет прозрачность фона виджета от 0 до 100%.", defaultOpacity, 0, 100, 1));
        this.fallback = fallback;
        setEnabled(false);
    }

    @Subscribe
    private void onHud(Render2DEvent event) {
        tickDelta = event.tickDelta();
        if (!(MinecraftClient.getInstance().currentScreen instanceof ChatScreen)) draw(event.context());
    }

    protected boolean canDraw() {
        var client = MinecraftClient.getInstance();
        return isEnabled() && client.player != null && !client.options.hudHidden
                && (client.currentScreen == null || isEditing());
    }

    protected final boolean isEditing() {
        var screen = MinecraftClient.getInstance().currentScreen;
        return screen instanceof ChatScreen || screen instanceof client.gui.screen.ClickGuiScreen;
    }

    @Override public final HudAnchor defaultHudAnchor() { return fallback.anchor(); }

    @Override public boolean isVisible() { return canDraw() && (content || isEditing()); }

    @Override public final void draw(DrawContext context) {
        if (context == null || !canDraw()) return;
        var client = MinecraftClient.getInstance();
        refresh(client, tickDelta);
        if (!content && !isEditing()) return;
        var runtime = ClientRuntime.getNullable();
        if (runtime == null || runtime.config() == null) return;
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        if (screenWidth <= 0 || screenHeight <= 0 || !(width > 0 && height > 0)) return;
        double usableWidth = screenWidth > 4 ? screenWidth - 4 : screenWidth;
        double usableHeight = screenHeight > 4 ? screenHeight - 4 : screenHeight;
        drawnScale = Math.min(scale.get(), Math.min(usableWidth / width, usableHeight / height));
        double sw = width * drawnScale, sh = height * drawnScale;
        HudPosition position = runtime.config().hudPosition(id(), fallback);
        double x = position.screenX(sw, screenWidth);
        double y = position.screenY(sh, screenHeight);
        x = Math.clamp(HudDragController.x(this, x), 0, Math.max(0, screenWidth - sw));
        y = Math.clamp(HudDragController.y(this, y), 0, Math.max(0, screenHeight - sh));
        bounds = new HudBounds(x, y, sw, sh);
        runtime.hudLayout().put(this, bounds);
        HudDragController.updateBounds(this, bounds);
        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().translate((float) x, (float) y);
            context.getMatrices().scale((float) drawnScale, (float) drawnScale);
            if (usePanel()) {int bg=hudBackground();UiRenderer.roundedRect(context,0,0,width,height,9,((int)Math.round(opacity.get()*255/100)<<24)|(bg&0xFFFFFF));}
            renderHud(context, client);
            if (client.currentScreen instanceof ChatScreen && (HudDragController.isDragging(this) || bounds.contains(HudDragController.mouseX(), HudDragController.mouseY())))
                UiRenderer.roundedBorder(context, 0, 0, width, height, 7, 1, 0xD95CE1E6);
        } finally { context.getMatrices().popMatrix(); }
    }

    @Override public final boolean onHudClick(double screenX, double screenY) {
        return bounds != null && bounds.contains(screenX, screenY)
                && handleClick((screenX - bounds.x()) / drawnScale, (screenY - bounds.y()) / drawnScale);
    }

    protected boolean hovered() {
        return isEditing() && bounds != null && bounds.contains(HudDragController.mouseX(), HudDragController.mouseY());
    }
    private static int hudBackground(){return client.api.Theme.background();}
    protected boolean handleClick(double x, double y) { return false; }
    protected boolean usePanel() { return true; }
    protected abstract void refresh(MinecraftClient client, float tickDelta);
    protected abstract void renderHud(DrawContext context, MinecraftClient client);
    protected final void title(DrawContext context, String title) {
        text(context, net.aero.visuals.localization.LocalText.get(title), 8, 7, 9, 0xFFF1F4FF);
        UiRenderer.roundedRect(context, 8, 21, width - 16, 1, 0.5, 0x25FFFFFF);
    }
    protected static void text(DrawContext context, String text, double x, double y, double size, int color) {
        UiRenderer.font(context, FontFace.GOLOS_SEMI, text, x, y, size, color);
    }
    protected static double textWidth(String text, double size) { return UiRenderer.fontWidth(FontFace.GOLOS_SEMI, text, size); }
    protected static String fit(String value, double room, double size) {
        return UiRenderer.fitFont(FontFace.GOLOS_SEMI, value, room, size, false);
    }
    protected static String seconds(int ticks) {
        long total = Math.max(0, (ticks + 19L) / 20);
        long seconds = total % 60;
        return total / 60 + (seconds < 10 ? ":0" : ":") + seconds;
    }
}
