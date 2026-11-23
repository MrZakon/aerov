package net.aero.visuals.gui;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.AeroVisuals;
import net.aero.visuals.animation.Easing;
import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import client.util.LegacyInput;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import static net.aero.visuals.localization.AeroI18n.tr;

/** Responsive AeroVisuals title menu with dynamic non-overlapping layout. */
public final class AeroTitleScreen extends Screen {
    private static final Identifier AERO_LOGO = AeroMenuChrome.logoTexture();
    private static final String TELEGRAM_URL = "https://t.me/AeroVisuals";
    private static final String WEBSITE_URL = "https://aeroproject.space/visuals/";
    private static final int TEXT = 0xFFF1F4FF;
    private static final int TEXT_2 = 0xFFE0E5FF;
    private static final int TEXT_3 = 0xFFB9C2E3;
    private static final int MUTED = 0xFF7683AA;
    private static final int MUTED_2 = 0xFF5F6D94;
    private static final int ACCENT = 0xFF5CE1E6;
    private static final int INK = 0xFF05080E;
    private static final int DANGER = 0xFFFF5C77;

    private long openedAt;
    private long exitArmedUntil;

    public AeroTitleScreen() {
        super(Component.translatable("aerovisuals.screen.title"));
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        try {
            AeroMenuChrome.titleBackground(context, width, height, mouseX, mouseY);
        } catch (Throwable t) {
            context.fill(0, 0, width, height, INK);
        }
        try {
            if (ClientRuntime.get().startupWelcome().render(context, width, height)) {
                super.extractRenderState(context, mouseX, mouseY, delta);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            Layout layout = layout();
            double reveal = ClientRuntime.get().settings().reducedMotion() ? 1
                    : Easing.EASE_OUT_CUBIC.applyAsDouble(Math.clamp(
                    (System.currentTimeMillis() - openedAt) / 450.0, 0.0, 1.0));
            drawLogo(context, layout);
            context.pose().pushMatrix();
            context.pose().translate(0, (float) ((1 - reveal) * 12 * layout.s));
            drawClock(context, layout);
            drawUsername(context, layout);
            drawMenuButtons(context, layout, mouseX, mouseY);
            drawSocialButtons(context, layout, mouseX, mouseY);
            context.pose().popMatrix();
        } catch (Throwable t) {
            AeroVisuals.LOGGER.warn("Error drawing AeroVisuals title screen", t);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawLogo(GuiGraphicsExtractor context, Layout layout) {
        UiRenderer.texture(context, AERO_LOGO, (width - layout.logo) / 2.0, layout.logoY,
                layout.logo, layout.logo, 128, 128);
    }

    private void drawClock(GuiGraphicsExtractor context, Layout layout) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String hh = time.substring(0, 2);
        String mm = time.substring(3, 5);
        double size = layout.clockSize;
        double digitGap = 6.6 * layout.s;
        double dot = 4.4 * layout.s;
        double total = UiRenderer.fontWidth(FontFace.PLEX_MONO, hh, size) + digitGap + dot + digitGap
                + UiRenderer.fontWidth(FontFace.PLEX_MONO, mm, size);
        double right = width - layout.left;
        double x = right - total;
        double y = layout.clockY;
        UiRenderer.font(context, FontFace.PLEX_MONO, hh, x, y, size, TEXT);
        double dotsX = x + UiRenderer.fontWidth(FontFace.PLEX_MONO, hh, size) + digitGap;
        UiRenderer.roundedRect(context, dotsX, y + size * 0.52, dot, dot, dot / 2, TEXT);
        UiRenderer.roundedRect(context, dotsX, y + size * 0.74, dot, dot, dot / 2, TEXT);
        UiRenderer.font(context, FontFace.PLEX_MONO, mm, dotsX + dot + digitGap, y, size, TEXT);

        Locale locale = Locale.forLanguageTag(minecraft.options.languageCode.replace('_', '-'));
        LocalDate now = LocalDate.now();
        String day = cap(now.getDayOfWeek().getDisplayName(TextStyle.SHORT, locale), locale) + ", ";
        String rest = now.getDayOfMonth() + " " + now.getMonth().getDisplayName(TextStyle.FULL, locale);
        double dateSize = 12.5 * layout.s;
        double dayW = UiRenderer.spacedFontWidth(FontFace.GOLOS, day, dateSize, 0.75);
        double restW = UiRenderer.spacedFontWidth(FontFace.GOLOS, rest, dateSize, 0.75);
        UiRenderer.spacedFont(context, FontFace.GOLOS, day, right - dayW - restW, y + size + 6 * layout.s,
                dateSize, 0.75, MUTED_2);
        UiRenderer.spacedFont(context, FontFace.GOLOS, rest, right - restW, y + size + 6 * layout.s,
                dateSize, 0.75, TEXT_3);
    }

    private void drawUsername(GuiGraphicsExtractor context, Layout layout) {
        String username = fit(FontFace.GOLOS_SEMI, net.aero.visuals.feature.ImportedFeatures.Streamer.display(minecraft.getUser().getName()),
                layout.buttonW, layout.usernameSize);
        double nameWidth = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, username, layout.usernameSize);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, username,
                layout.buttonX + (layout.buttonW - nameWidth) / 2.0,
                layout.usernameY, layout.usernameSize, TEXT);
    }

    private void drawMenuButtons(GuiGraphicsExtractor context, Layout layout, double mouseX, double mouseY) {
        double y = layout.buttonY;
        double step = layout.buttonH + layout.gap;
        sourceButton(context, layout, Icon.GLOBE, tr("title.multiplayer"), layout.buttonX, y,
                layout.buttonW, true, false, mouseX, mouseY);
        sourceButton(context, layout, Icon.PERSON, tr("title.singleplayer"), layout.buttonX, y + step,
                layout.buttonW, false, false, mouseX, mouseY);
        sourceButton(context, layout, Icon.PERSON, tr("title.accounts"), layout.buttonX, y + step * 2,
                layout.buttonW, false, false, mouseX, mouseY);
        double half = (layout.buttonW - layout.gap) / 2;
        sourceButton(context, layout, Icon.PERSON, tr("title.settings"), layout.buttonX,
                y + step * 3, half, false, false, mouseX, mouseY);
        sourceButton(context, layout, Icon.POWER,
                System.currentTimeMillis() < exitArmedUntil ? tr("action.confirm") : tr("title.quit"),
                layout.buttonX + half + layout.gap, y + step * 3, half, false, true, mouseX, mouseY);
    }

    private void sourceButton(GuiGraphicsExtractor context, Layout layout, Icon icon, String label,
                              double x, double y, double w, boolean primary, boolean danger,
                              double mouseX, double mouseY) {
        boolean hover = UiRenderer.inside(mouseX, mouseY, x, y, w, layout.buttonH);
        double dy = y - (hover ? 1 : 0);
        int background = primary ? (hover ? 0x9C000000 : 0x78000000)
                : (hover ? 0x80000000 : 0x58000000);
        UiRenderer.roundedRect(context, x, dy, w, layout.buttonH, 8, background);
        UiRenderer.roundedBorder(context, x, dy, w, layout.buttonH, 8, 1,
                hover ? ColorUtil.withAlpha(danger ? DANGER : 0xFFFFFFFF, danger ? 140 : 90)
                        : 0x30FFFFFF);
        int color = danger ? DANGER : TEXT_2;
        FontFace face = FontFace.UNBOUNDED_LIGHT;
        double iconSize = Math.min(13 * layout.s, layout.buttonH * 0.34);
        double fontSize = layout.buttonFont;
        boolean compact = w < layout.buttonW * 0.75;
        double pad = compact ? Math.max(6, 9 * layout.s) : Math.max(10, 14 * layout.s);
        double iconGap = compact ? Math.max(4, 6 * layout.s) : Math.max(6, 8 * layout.s);
        double maxLabel = Math.max(8, w - pad * 2 - iconSize - iconGap);
        double naturalWidth = UiRenderer.fontWidth(face, label, fontSize);
        if (naturalWidth > maxLabel) fontSize *= maxLabel / naturalWidth;
        String fitted = label;
        double labelW = UiRenderer.fontWidth(face, fitted, fontSize);
        double contentW = iconSize + iconGap + labelW;
        double ix = x + Math.max(pad, (w - contentW) / 2);
        double iy = dy + (layout.buttonH - iconSize) / 2;
        double ty = dy + (layout.buttonH - fontSize) / 2;
        drawIcon(context, icon, ix, iy, iconSize, danger ? color : TEXT_3);
        UiRenderer.font(context, face, fitted, ix + iconSize + iconGap, ty, fontSize, color);
    }

    private void drawIcon(GuiGraphicsExtractor context, Icon icon, double x, double y, double size, int color) {
        String glyph = switch (icon) {
            case GLOBE -> "C";
            case PERSON -> "A";
            case POWER -> "D";
        };
        UiRenderer.font(context, FontFace.ICONS_2, glyph, x, y, size, color);
    }

    private void drawSocialButtons(GuiGraphicsExtractor context, Layout layout, double mouseX, double mouseY) {
        for (int i = 0; i < 2; i++) {
            double x = layout.left + i * (layout.chip + 9 * layout.s);
            boolean hover = UiRenderer.inside(mouseX, mouseY, x, layout.chipY, layout.chip, layout.chip);
            double y = layout.chipY - (hover ? 2 : 0);
            UiRenderer.verticalGradientRoundedRect(context, x, y, layout.chip, layout.chip, 8,
                    0xD9101426, 0xD9070A15);
            UiRenderer.roundedRect(context, x + 8, y + 1, layout.chip - 16, 1, 0.5, 0x0DFFFFFF);
            UiRenderer.roundedBorder(context, x, y, layout.chip, layout.chip, 8, 1, hover ? 0x3DFFFFFF : 0x17FFFFFF);
            if (i == 1) {
                double logoSize = layout.chip - Math.max(4, 6 * layout.s);
                AeroMenuChrome.logo(context, x + (layout.chip - logoSize) / 2.0,
                        y + (layout.chip - logoSize) / 2.0, logoSize);
            } else {
                double iw = UiRenderer.fontWidth(FontFace.ICONS_2, "E", 17 * layout.s);
                UiRenderer.font(context, FontFace.ICONS_2, "E", x + (layout.chip - iw) / 2,
                        y + (layout.chip - 17 * layout.s) / 2, 17 * layout.s, hover ? ACCENT : MUTED);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (ClientRuntime.get().startupWelcome().isActive()) {
            ClientRuntime.get().startupWelcome().skip();
            openedAt = System.currentTimeMillis();
            return true;
        }
        if (LegacyInput.button(click.button()) != LegacyInput.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(click, doubled);
        Layout layout = layout();
        double x = click.x();
        double y = click.y();
        double buttonY = layout.buttonY;

        double step = layout.buttonH + layout.gap;
        if (hit(x, y, layout.buttonX, buttonY, layout.buttonW, layout.buttonH)) {
            minecraft.gui.setScreen(new JoinMultiplayerScreen(this));
        } else if (hit(x, y, layout.buttonX, buttonY + step, layout.buttonW, layout.buttonH)) {
            minecraft.gui.setScreen(new AeroWorldSelectScreen(this));
        } else if (hit(x, y, layout.buttonX, buttonY + step * 2, layout.buttonW, layout.buttonH)) {
            minecraft.gui.setScreen(new AltManagerScreen(this));
        } else if (hit(x, y, layout.buttonX, buttonY + step * 3, (layout.buttonW - layout.gap) / 2, layout.buttonH)) {
            minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options));
        } else if (hit(x, y, layout.buttonX + (layout.buttonW + layout.gap) / 2, buttonY + step * 3, (layout.buttonW - layout.gap) / 2, layout.buttonH)) {
            if (System.currentTimeMillis() < exitArmedUntil) minecraft.stop();
            else exitArmedUntil = System.currentTimeMillis() + 3000;
        } else if (hit(x, y, layout.left, layout.chipY, layout.chip, layout.chip)) {
            net.aero.visuals.render.PlatformLinks.openUri(TELEGRAM_URL);
        } else if (hit(x, y, layout.left + layout.chip + 9 * layout.s, layout.chipY, layout.chip, layout.chip)) {
            net.aero.visuals.render.PlatformLinks.openUri(WEBSITE_URL);
        } else {
            return super.mouseClicked(click, doubled);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (ClientRuntime.get().startupWelcome().isActive()) {
            ClientRuntime.get().startupWelcome().skip();
            openedAt = System.currentTimeMillis();
            return true;
        }
        return super.keyPressed(input);
    }

    private Layout layout() {
        double s = Math.clamp(Math.min(width / 1600.0, height / 900.0), 0.38, 1.12);
        double left = Math.max(18, 56 * s);
        double gap = Math.max(6, 9 * s);
        double chip = Math.max(28, 38 * s);
        double logo = Math.max(40, 66 * s);
        double footerSize = Math.max(8, 11 * s);
        double usernameSize = Math.max(18, 38 * s);
        double buttonH = Math.max(32, 50 * s);
        double buttonW = Math.min(Math.max(160, 340 * s), Math.max(80, width - left * 2));
        double logoY = Math.max(8, 18 * s);
        double clockY = Math.max(10, 20 * s);
        double bottomPad = Math.max(12, 22 * s);
        double footerY = height - bottomPad - footerSize * 1.45 * 2;
        double chipY = footerY - Math.max(8, 14 * s) - chip;
        double nameGap = Math.max(10, 14 * s);
        double minTop = logoY + logo + Math.max(8, 16 * s) + usernameSize + nameGap;
        double maxBottom = footerY - Math.max(10, 14 * s);
        double available = Math.max(40, maxBottom - minTop);
        if (buttonH * 4 + gap * 3 > available) {
            gap = Math.min(gap, 4);
            buttonH = Math.min(buttonH, Math.max(8, (available - gap * 3) / 4));
        }
        double buttonsH = buttonH * 4 + gap * 3;
        double buttonY = Math.clamp(height * 0.60 - buttonsH / 2.0,
                minTop, Math.max(minTop, maxBottom - buttonsH));
        double usernameY = buttonY - usernameSize - nameGap;
        double buttonFont = Math.max(6.8, Math.min(buttonH * 0.24, 10.5));
        return new Layout(s, left, (width - buttonW) / 2.0, buttonW, buttonH, gap,
                chip, logo, logoY, clockY, Math.max(18, 44 * s), usernameY, usernameSize,
                buttonY, buttonFont, chipY);
    }

    private static boolean hit(double mx, double my, double x, double y, double w, double h) {
        return UiRenderer.inside(mx, my, x, y, w, h);
    }

    private static String cap(String value, Locale locale) {
        return value == null || value.isEmpty() ? value
                : value.substring(0, 1).toUpperCase(locale) + value.substring(1);
    }

    private static String fit(FontFace face, String value, double maxWidth, double size) {
        return UiRenderer.fitFont(face, value, maxWidth, size, true);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    private enum Icon { GLOBE, PERSON, POWER }

    private record Layout(double s, double left, double buttonX, double buttonW, double buttonH, double gap,
                          double chip, double logo, double logoY,
                          double clockY, double clockSize, double usernameY, double usernameSize,
                          double buttonY, double buttonFont, double chipY) {}
}
