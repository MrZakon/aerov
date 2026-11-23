package net.aero.visuals.module.impl.misc;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.Render2DEvent;
import net.aero.visuals.hud.HudAnchor;
import net.aero.visuals.hud.HudBounds;
import net.aero.visuals.hud.HudPosition;
import net.aero.visuals.hud.HudDragController;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.BooleanSetting;
import net.aero.visuals.module.setting.ColorSetting;
import net.aero.visuals.module.setting.ColorValue;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class IslandModule extends Module implements net.aero.visuals.hud.DraggableHud {
    private static final double FONT_SIZE = 8.5;
    private static final double HEIGHT = 32;
    private static final double PADDING = 12;
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
    private String fittedText = "";
    private String lastText = "";
    private double lastRoom = -1;
    private double textWidth;
    private int widthOptions=-1;
    private double panelWidth;
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
        if (!(MinecraftClient.getInstance().currentScreen instanceof ChatScreen)) draw(event.context());
    }

    public boolean isVisible() {
        var client = MinecraftClient.getInstance();
        if (!isEnabled() || client.player == null || client.options.hudHidden || client.options.playerListKey.isPressed()) return false;
        return client.currentScreen == null || client.currentScreen instanceof ChatScreen
                || client.currentScreen instanceof client.gui.screen.ClickGuiScreen;
    }

    public void migrateBackgroundOpacity() {
        ColorValue value = background.get();
        int alpha = value.primaryArgb() >>> 24;
        if (alpha == 255) return;
        if (!value.rainbow()) opacity.set(opacity.get() * alpha / 255);
        background.set(new ColorValue(value.primaryArgb() | 0xFF000000, value.secondaryArgb() | 0xFF000000,
                value.rainbow(), value.rainbowSpeed()));
    }


    public void draw(DrawContext context) {
        if (context == null || !isVisible()) return;
        var client = MinecraftClient.getInstance();
        boolean editing = client.currentScreen instanceof ChatScreen;
        var runtime = ClientRuntime.getNullable();
        if (runtime == null || runtime.config() == null) return;
        double scale = size.get();
        // Reserve the numeric slots once, independently of track titles and playback.
        double logicalWidth=Math.min(panelWidth(),
                Math.max(1,(context.getScaledWindowWidth()-4)/scale));
        double room = Math.max(1,logicalWidth-PADDING*2);
        String text = sample(client);
        if (!text.equals(lastText) || Math.abs(room - lastRoom) > 0.01) {
            lastText = text; lastRoom = room;
            fittedText = fit(text, room);
            textWidth = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, fittedText, FONT_SIZE);
        }
        var media=runtime.module(net.aero.visuals.module.impl.hud.MediaPlayerModule.class);
        // Once opening starts, keep the full controls area interactive while it unfolds.
        // Moving toward the next-track button must not immediately collapse the card.
        var hoverBounds=combinedBounds==null?null:new HudBounds(combinedBounds.x(),combinedBounds.y(),combinedBounds.width(),
                Math.max(combinedBounds.height(),(HEIGHT+(media==null?0:media.combinedInteractionHeight()))*scale));
        boolean hover=editing&&hoverBounds!=null&&hoverBounds.contains(HudDragController.mouseX(),HudDragController.mouseY());
        musicVisible=media!=null&&media.prepareCombined(hover,logicalWidth);
        double logicalHeight=HEIGHT+(musicVisible?media.combinedHeight():0);
        double width = logicalWidth * scale;
        double height = logicalHeight * scale;
        double x = Math.max(0, (context.getScaledWindowWidth() - width) / 2.0);
        double y = Math.min(7, Math.max(0, context.getScaledWindowHeight() - HEIGHT*scale));
        var bounds = new HudBounds(x, y, width, height);combinedBounds=bounds;combinedScale=scale;mediaX=musicVisible?(logicalWidth-media.combinedWidth())/2:0;
        runtime.hudLayout().put(this, bounds);
        HudDragController.updateBounds(this, bounds);

        int backgroundColor = background.get().eval();
        int alpha = (int) Math.round(255 * opacity.get() / 100);
        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().translate((float) x, (float) y);
            context.getMatrices().scale((float) scale, (float) scale);
            if (alpha > 0) UiRenderer.roundedRect(context, 0, 0, logicalWidth, logicalHeight, 12, ColorUtil.withAlpha(backgroundColor, alpha));
            UiRenderer.font(context, FontFace.GOLOS_SEMI, fittedText, (logicalWidth-textWidth)/2, (HEIGHT - FONT_SIZE) / 2, FONT_SIZE, accent.get().eval());
            if(musicVisible){
                UiRenderer.roundedRect(context,PADDING,HEIGHT-1,Math.max(0,logicalWidth-PADDING*2),1,.5,
                        ColorUtil.withAlpha(0xFFFFFFFF,(int)(34*media.combinedAlpha())));
                media.drawCombined(context,mediaX,HEIGHT);
            }
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    @Override public boolean onHudClick(double x,double y){if(!musicVisible||combinedBounds==null||!combinedBounds.contains(x,y))return false;var media=ClientRuntime.get().module(net.aero.visuals.module.impl.hud.MediaPlayerModule.class);double ly=(y-combinedBounds.y())/combinedScale-HEIGHT;return ly>=0&&media!=null&&media.clickCombined((x-combinedBounds.x())/combinedScale-mediaX,ly);}
    private double panelWidth() {
        int options=(fps.get()?1:0)|(ping.get()?2:0)|(clock.get()?4:0);
        if(options==widthOptions)return panelWidth;
        char digit='0';double widest=0;
        for(char value='0';value<='9';value++){double width=UiRenderer.fontWidth(FontFace.GOLOS_SEMI,String.valueOf(value),FONT_SIZE);if(width>widest){widest=width;digit=value;}}
        String digits=String.valueOf(digit).repeat(3);
        StringBuilder line=new StringBuilder("AeroVisuals");
        if(fps.get())line.append("  ·  ").append(digits).append(" FPS");
        if(ping.get())line.append("  ·  ").append(digits).append(" ms");
        if(clock.get())line.append("  ·  ").append(digit).append(digit).append(':').append(digit).append(digit);
        panelWidth=UiRenderer.fontWidth(FontFace.GOLOS_SEMI,line.toString(),FONT_SIZE)+PADDING*2;
        widthOptions=options;
        return panelWidth;
    }
    private String sample(MinecraftClient client) {
        long now = System.nanoTime();
        if (now < nextSample) return sampledText;
        nextSample = now + 200_000_000L;
        StringBuilder line = new StringBuilder("AeroVisuals");
        if (fps.get()) line.append("  ·  ").append(Math.max(0, client.getCurrentFps())).append(" FPS");
        if (ping.get()) {
            var network = client.getNetworkHandler();
            var entry = network == null ? null : network.getPlayerListEntry(client.player.getUuid());
            if (client.isInSingleplayer()) line.append("  ·  0 ms");
            else if (entry != null) line.append("  ·  ").append(Math.max(0, entry.getLatency())).append(" ms");
        }
        if (clock.get()) line.append("  ·  ").append(CLOCK.format(LocalTime.now()));
        sampledText = line.toString();
        return sampledText;
    }

    private static String fit(String value, double room) {
        return UiRenderer.fitFont(FontFace.GOLOS_SEMI, value, room, FONT_SIZE, false);
    }
}
