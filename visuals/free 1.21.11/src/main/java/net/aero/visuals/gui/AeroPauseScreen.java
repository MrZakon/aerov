package net.aero.visuals.gui;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import static net.aero.visuals.localization.AeroI18n.tr;

public final class AeroPauseScreen extends Screen {
    private static final double DESIGN_W = 920;
    private static final double DESIGN_H = 540;
    private static final double MENU_W = 338;
    private static final double ROW_H = 44;
    private static final double GAP = 8;
    private static final double MENU_X = (DESIGN_W - MENU_W) / 2;
    private static final double MENU_Y = (DESIGN_H - (ROW_H * 3 + GAP * 2)) / 2;

    public AeroPauseScreen() {
        super(Text.translatable("aerovisuals.screen.pause"));
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height,
                ClientRuntime.get().settings().menuDimming() ? 0x26000000 : 0x10000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        AeroMenuChrome.Viewport view = AeroMenuChrome.windowViewport(width, height, DESIGN_W, DESIGN_H);
        double mx = view.mouseX(mouseX);
        double my = view.mouseY(mouseY);
        view.begin(context);
        AeroMenuChrome.centeredLogo(context, DESIGN_W);

        String heading = tr("pause.game_paused");
        double headingSize = Math.min(29, MENU_W / Math.max(1, UiRenderer.fontWidth(FontFace.GOLOS_SEMI, heading, 1)));
        double headingWidth = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, heading, headingSize);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, heading,
                (DESIGN_W - headingWidth) / 2, MENU_Y - headingSize - 20,
                headingSize, AeroMenuChrome.TEXT);

        menuButton(context, tr("action.continue"), MENU_Y, false, mx, my);
        menuButton(context, tr("title.minecraft_options"), MENU_Y + ROW_H + GAP, false, mx, my);
        menuButton(context, tr("action.disconnect"), MENU_Y + (ROW_H + GAP) * 2, true, mx, my);
        view.end(context);
    }

    private void menuButton(DrawContext context, String label, double y,
                            boolean danger, double mouseX, double mouseY) {
        boolean hovered = UiRenderer.inside(mouseX, mouseY, MENU_X, y, MENU_W, ROW_H);
        UiRenderer.roundedRect(context, MENU_X, y, MENU_W, ROW_H, 10,
                hovered ? 0xA0000000 : 0x80000000);
        UiRenderer.roundedBorder(context, MENU_X, y, MENU_W, ROW_H, 10, 1,
                hovered ? (danger ? 0x90FF5C77 : 0x60FFFFFF) : 0x30FFFFFF);
        double fontSize = Math.min(12.5,
                (MENU_W - 36) / Math.max(1, UiRenderer.fontWidth(FontFace.GOLOS_SEMI, label, 1)));
        double textWidth = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, label, fontSize);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, label,
                MENU_X + (MENU_W - textWidth) / 2, y + (ROW_H - fontSize * 1.2) / 2,
                fontSize, danger ? AeroMenuChrome.DANGER : AeroMenuChrome.TEXT);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(click, doubled);
        AeroMenuChrome.Viewport view = AeroMenuChrome.windowViewport(width, height, DESIGN_W, DESIGN_H);
        double x = view.mouseX(click.x());
        double y = view.mouseY(click.y());
        if (hit(x, y, MENU_Y)) {
            close();
        } else if (hit(x, y, MENU_Y + ROW_H + GAP)) {
            client.setScreen(new OptionsScreen(this, client.options));
        } else if (hit(x, y, MENU_Y + (ROW_H + GAP) * 2)) {
            client.disconnect(new AeroTitleScreen(), false);
        } else {
            return super.mouseClicked(click, doubled);
        }
        GuiSound.play("clickgui_open");
        return true;
    }

    private static boolean hit(double mx, double my, double y) {
        return UiRenderer.inside(mx, my, MENU_X, y, MENU_W, ROW_H);
    }
}
