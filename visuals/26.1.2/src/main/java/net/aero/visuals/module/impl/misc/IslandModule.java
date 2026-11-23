package net.aero.visuals.module.impl.misc;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.Render2DEvent;
import net.aero.visuals.hud.HudBounds;
import net.aero.visuals.hud.HudDragController;
import net.aero.visuals.mixin.BossHealthOverlayAccessor;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.BooleanSetting;
import net.aero.visuals.module.setting.ColorSetting;
import net.aero.visuals.module.setting.ColorValue;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.module.impl.hud.MediaPlayerModule;
import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class IslandModule extends Module implements net.aero.visuals.hud.DraggableHud {
    private static final double FONT_SIZE = 8.5;
    private static final double HEIGHT = 16;
    private static final double PADDING = 5;
    private static final double MEDIA_WIDTH = 150;
    private static final int TOP_MARGIN = 5;
    private static final int BOSS_BAR_START = 12;
    private static final int BOSS_BAR_HEIGHT = 5;
    private static final int BOSS_BAR_STEP = 19;
    private static final String FONT_SEPARATOR = " · ";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private final DoubleSetting size = add(new DoubleSetting(
            "scale", "Масштаб", "Изменяет размер островка.", 0.8, 0.5, 2, 0.05));
    private final DoubleSetting opacity = add(new DoubleSetting(
            "background_opacity", "Непрозрачность фона (%)",
            "Прозрачность фона островка от 0 до 100%. Цвет текста настраивается отдельно.", 97, 0, 100, 1));
    private final ColorSetting background = add(new ColorSetting(
            "background_color", "Цвет островка", "Выберите цвет фона островка в палитре.", ColorValue.solid(0xFF050608), false));
    private final ColorSetting accent = add(new ColorSetting(
            "accent", "Цвет текста", "Выберите цвет надписи AeroVisuals и показателей островка.", ColorValue.solid(0xFFF4FFF8)));
    private final BooleanSetting fps = add(new BooleanSetting("fps", "FPS", "Показывать частоту кадров внутри островка.", true));
    private final BooleanSetting ping = add(new BooleanSetting("ping", "Пинг", "Показывать задержку соединения внутри островка.", true));
    private final BooleanSetting clock = add(new BooleanSetting("clock", "Время", "Показывать местное время внутри островка.", true));
    private long nextSample;
    private String sampledText = "";
    private String measuredText = "";
    private double measuredWidth;
    private double panelWidth;
    private int widthOptions = -1;
    private HudBounds combinedBounds;private double combinedScale,mediaX;private boolean musicVisible;

    public IslandModule() {
        super("wm_island", "Island",
                "Постоянный островок AeroVisuals с FPS, пингом и временем. Скрывается при TAB. "
                        + "Закреплён сверху по центру экрана вместе с медиаплеером.", ModuleCategory.HUD);
        fps.onChanged(ignored -> nextSample = 0);
        ping.onChanged(ignored -> nextSample = 0);
        clock.onChanged(ignored -> nextSample = 0);
        setEnabled(true);
    }

    @Override
    public boolean isLocked() { return true; }

    @Override public boolean isDraggable() { return false; }

    @Subscribe
    private void onRender(Render2DEvent event) {
        if (!(Minecraft.getInstance().screen instanceof ChatScreen)) draw(event.context());
    }

    public boolean isVisible() {
        var client = Minecraft.getInstance();
        if (!isEnabled() || client.player == null || client.options.hideGui || client.options.keyPlayerList.isDown()) return false;
        return client.screen == null || client.screen instanceof ChatScreen
                || client.screen instanceof client.gui.screen.ClickGuiScreen;
    }

    public void migrateBackgroundOpacity() {
        ColorValue value = background.get();
        int alpha = value.primaryArgb() >>> 24;
        if (alpha == 255) return;
        if (!value.rainbow()) opacity.set(opacity.get() * alpha / 255);
        background.set(new ColorValue(value.primaryArgb() | 0xFF000000, value.secondaryArgb() | 0xFF000000,
                value.rainbow(), value.rainbowSpeed()));
    }


    public void draw(GuiGraphicsExtractor context) {
        if (context == null || !isVisible()) return;
        var client = Minecraft.getInstance();
        boolean editing = client.screen instanceof ChatScreen;
        var runtime = ClientRuntime.getNullable();
        if (runtime == null || runtime.config() == null) return;
        double scale = size.get();
        String text = sample(client);
        if (!text.equals(measuredText)) {
            measuredText = text;
            measuredWidth = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, text, FONT_SIZE);
        }
        double availableWidth = Math.max(1, context.guiWidth() - 4);
        double logicalWidth = Math.max(MEDIA_WIDTH, panelWidth());
        double y = top(client, context.guiHeight());
        // Shrink the header on narrow windows so the enabled values remain readable together.
        scale = Math.min(scale, availableWidth / logicalWidth);
        scale = Math.min(scale, Math.max(1, context.guiHeight() - y) / HEIGHT);
        var media = runtime.module(MediaPlayerModule.class);
        boolean hover = false;
        if (editing && combinedBounds != null) {
            var hoverBounds = new HudBounds(combinedBounds.x(), y, combinedBounds.width(),
                    Math.max(combinedBounds.height(), (HEIGHT + (media == null ? 0 : media.combinedInteractionHeight())) * scale));
            hover = hoverBounds.contains(HudDragController.mouseX(), HudDragController.mouseY());
        }
        musicVisible = media != null && media.prepareCombined(hover, logicalWidth);
        double logicalHeight=HEIGHT+(musicVisible?media.combinedHeight():0);
        double width = logicalWidth * scale;
        double height = logicalHeight * scale;
        double x = Math.max(0, (context.guiWidth() - width) / 2.0);
        var bounds = new HudBounds(x, y, width, height);combinedBounds=bounds;combinedScale=scale;mediaX=musicVisible?(logicalWidth-media.combinedWidth())/2:0;
        runtime.hudLayout().put(this, bounds);
        HudDragController.updateBounds(this, bounds);

        int backgroundColor = background.get().eval();
        int alpha = (int) Math.round(255 * opacity.get() / 100);
        context.pose().pushMatrix();
        try {
            context.pose().translate((float) x, (float) y);
            context.pose().scale((float) scale, (float) scale);
            if (alpha > 0) UiRenderer.roundedRect(context, 0, 0, logicalWidth, logicalHeight, 6, ColorUtil.withAlpha(backgroundColor, alpha));
            double textScale = measuredWidth == 0 ? 1 : Math.min(1, (logicalWidth - 2 * PADDING) / measuredWidth);
            double textSize = FONT_SIZE * textScale;
            UiRenderer.font(context, FontFace.GOLOS_SEMI, text, (logicalWidth - measuredWidth * textScale) / 2,
                    (HEIGHT - textSize) / 2, textSize, accent.get().eval());
            if(musicVisible){
                UiRenderer.roundedRect(context,PADDING,HEIGHT-1,Math.max(0,logicalWidth-PADDING*2),1,.5,
                        ColorUtil.withAlpha(0xFFFFFFFF,(int)(34*media.combinedAlpha())));
                media.drawCombined(context,mediaX,HEIGHT);
            }
        } finally {
            context.pose().popMatrix();
        }
    }

    @Override public boolean onHudClick(double x,double y){if(!musicVisible||combinedBounds==null||!combinedBounds.contains(x,y))return false;var media=ClientRuntime.get().module(net.aero.visuals.module.impl.hud.MediaPlayerModule.class);double ly=(y-combinedBounds.y())/combinedScale-HEIGHT;return ly>=0&&media!=null&&media.clickCombined((x-combinedBounds.x())/combinedScale-mediaX,ly);}
    private static int top(Minecraft client, int guiHeight) {
        var overlay = (BossHealthOverlayAccessor) client.gui.getBossOverlay();
        int activeBars = overlay.aero$bossEvents().size();
        if (activeBars == 0) return TOP_MARGIN;
        // Match BossHealthOverlay: draw at least one bar, then stop after reaching the top third.
        int visibleLimit = Math.max(1, Math.ceilDiv(guiHeight / 3 - BOSS_BAR_START, BOSS_BAR_STEP));
        int lastBarY = BOSS_BAR_START + (Math.min(activeBars, visibleLimit) - 1) * BOSS_BAR_STEP;
        return lastBarY + BOSS_BAR_HEIGHT + TOP_MARGIN;
    }
    private double panelWidth() {
        int options=(fps.get()?1:0)|(ping.get()?2:0)|(clock.get()?4:0);
        if(options==widthOptions)return panelWidth;
        char digit='0';double widest=0;
        for(char value='0';value<='9';value++){double width=UiRenderer.fontWidth(FontFace.GOLOS_SEMI,String.valueOf(value),FONT_SIZE);if(width>widest){widest=width;digit=value;}}
        String digits=String.valueOf(digit).repeat(4);
        StringBuilder line=new StringBuilder("AeroVisuals");
        if(fps.get())line.append(FONT_SEPARATOR).append(digits).append(" FPS");
        if(ping.get())line.append(FONT_SEPARATOR).append(digits).append(" ms");
        if(clock.get())line.append(FONT_SEPARATOR).append(digit).append(digit).append(':').append(digit).append(digit);
        panelWidth=UiRenderer.fontWidth(FontFace.GOLOS_SEMI,line.toString(),FONT_SIZE)+PADDING*2;
        widthOptions=options;
        return panelWidth;
    }
    private String sample(Minecraft client) {
        long now = System.nanoTime();
        if (now < nextSample) return sampledText;
        nextSample = now + 200_000_000L;
        StringBuilder line = new StringBuilder("AeroVisuals");
        if (fps.get()) line.append(FONT_SEPARATOR).append(Math.max(0, client.getFps())).append(" FPS");
        if (ping.get()) {
            var network = client.getConnection();
            var entry = network == null ? null : network.getPlayerInfo(client.player.getUUID());
            if (client.isLocalServer()) line.append(FONT_SEPARATOR).append("0 ms");
            else if (entry != null) line.append(FONT_SEPARATOR).append(Math.max(0, entry.getLatency())).append(" ms");
        }
        if (clock.get()) line.append(FONT_SEPARATOR).append(CLOCK.format(LocalTime.now()));
        sampledText = line.toString();
        return sampledText;
    }

}
