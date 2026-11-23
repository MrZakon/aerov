package net.aero.visuals.gui;

import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

/** Список серверов сохраняет ванильную совместимость, меняя визуальный слой. */
public final class AeroMultiplayerScreen extends MultiplayerScreen {
    public AeroMultiplayerScreen(Screen parent) {
        super(parent);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_F5) {
            // Refresh the official model in place. Recreating the screen here used to race the
            // add/edit callbacks and could leave the widget with only the last saved entry.
            getServerList().loadFile();
            if (serverListWidget != null) {
                serverListWidget.setSelected(null);
                serverListWidget.setServers(getServerList());
            }
            updateButtonActivationStates();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    protected void refreshWidgetPositions() {
        super.refreshWidgetPositions();
        if (serverListWidget == null) return;
        int panelWidth = Math.min(680, Math.max(320, width - 72));
        int panelX = (width - panelWidth) / 2;
        int panelY = Math.max(48, Math.min(58, height - 260));
        int listBottom = Math.max(panelY + 80, height - 74);
        int panelHeight = Math.max(80, listBottom - panelY);
        // EntryListWidget owns the coordinates of every child row. Moving only the outer
        // ClickableWidget left the server entries at their old vanilla Y position, so they
        // appeared to float above the custom panel and their hit boxes no longer matched.
        serverListWidget.position(panelWidth, panelHeight, panelX, panelY);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        net.aero.visuals.render.MenuLiquidGlass.prepare(width, height, mouseX, mouseY);
        AeroMenuChrome.background(context, context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                mouseX, mouseY, false);
        AeroMenuChrome.microBrand(context);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelWidth = Math.min(680, Math.max(320, width - 72));
        int panelX = (width - panelWidth) / 2;
        int panelY = Math.max(48, Math.min(58, height - 260));
        int winX = panelX - 8;
        int winY = panelY - 28;
        int winW = panelWidth + 16;
        int winH = (height - 6) - winY;

        UiRenderer.shadow(context, winX, winY, winW, winH, 20, ColorUtil.withAlpha(0xFF000000, 160));
        AeroMenuChrome.glass(context, winX, winY, winW, winH, 20);
        UiRenderer.spacedFont(context, FontFace.UNBOUNDED_LIGHT,
                "AEROVISUALS / " + getTitle().getString().toUpperCase(java.util.Locale.ROOT),
                panelX + 8, winY + 11, 6.5, 0.9, AeroMenuChrome.GREEN);
        super.render(context, mouseX, mouseY, delta);
    }
}
