package net.aero.visuals.module.impl.hud;

import net.aero.visuals.hud.HudAnchor;
import net.aero.visuals.hud.HudPosition;
import net.aero.visuals.hud.HudDragController;
import net.aero.visuals.media.MediaArtHelper;
import net.aero.visuals.media.WindowsMediaTracker;
import net.aero.visuals.module.setting.BooleanSetting;
import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class MediaPlayerModule extends HudWidgetModule {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private final BooleanSetting fps = new BooleanSetting("fps", "", "", false);
    private final BooleanSetting ping = new BooleanSetting("ping", "", "", false);
    private final BooleanSetting clock = new BooleanSetting("clock", "", "", false);
    private final BooleanSetting expand = add(new BooleanSetting("expand_on_hover", "Раскрывать при наведении", "При наведении в чате открывать обложку, исполнителя, прогресс и кнопки управления.", true));
    private boolean combinedHover,combinedDrawing;
    private float expandAnim;
    private double expandProgress;
    private long lastFrameTime;
    private long lastPlayingTime;
    private WindowsMediaTracker.MediaTrack lastValidTrack;
    private String snapshotArtworkPath = "";
    private boolean wasExplicitlyPlaying;
    private double visibility;private float visualAlpha;private double layoutWidth=260;
    private int snapshotPosition,snapshotDuration;

    public MediaPlayerModule() {
        super("media_player", "MediaPlayer", "Медиаплеер из старых исходников: название трека, исполнитель, обложка, прогресс, пауза и переключение треков через медиасессию Windows. Для управления откройте чат.", new HudPosition(HudAnchor.TOP_CENTER, 0, 47, 1), 98);
        setEnabled(true);
    }

    private WindowsMediaTracker.MediaTrack getEffectiveTrack() {
        return visibility > 0 ? lastValidTrack : null;
    }
    @Override public boolean isLocked(){return true;}
    @Override public boolean isDraggable(){return false;}

    @Override protected void onEnable() { WindowsMediaTracker.get().start(); }

    @Override protected boolean canDraw() { return false; }
    @Override protected boolean usePanel() { return false; }
    @Override protected void onDisable() {
        MediaArtHelper.close();
        expandAnim = 0;
        expandProgress = 0;
        lastFrameTime = 0;
        lastValidTrack = null;
        snapshotArtworkPath = "";
        wasExplicitlyPlaying = false;
        visibility = 0;
        visualAlpha = 0;
    }

    @Override protected void refresh(Minecraft client, float tickDelta) {
        var tracker = WindowsMediaTracker.get();
        var playback = tracker.playback();
        var live = playback.track();
        boolean playing = playback.playing() && !live.title().isBlank();
        content=playing;long now=System.nanoTime();double dt=lastFrameTime==0?.016:Math.clamp((now-lastFrameTime)/1e9,0,.1);lastFrameTime=now;
        if (playing) {
            lastValidTrack = live;
            lastPlayingTime = System.currentTimeMillis();
            snapshotPosition = playback.interpolatedPositionMs();
            snapshotDuration = playback.durationMs();
            snapshotArtworkPath = playback.artworkPath();
        }
        boolean reduced=net.aero.visuals.ClientRuntime.get().settings().reducedMotion();
        // Reduced motion shortens these transitions instead of removing them entirely.
        visibility=Math.clamp(visibility+(playing?1:-1)*dt/(reduced?.16:(playing?.28:.22)),0,1);
        visualAlpha=(float)(visibility*visibility*(3-2*visibility));
        boolean opening=playing&&expand.get()&&combinedHover;
        double duration=reduced?.18:(opening?.36:.30);
        expandProgress=Math.clamp(expandProgress+(opening?1:-1)*dt/duration,0,1);
        // One reversible timeline drives the panel, cover, title and controls.
        expandAnim=(float)(expandProgress*expandProgress*(3-2*expandProgress));
        if (visibility <= 0) {
            if (lastValidTrack != null) MediaArtHelper.close();
            lastValidTrack = null;
            snapshotArtworkPath = "";
            combinedHover = false;
        }
        var track=lastValidTrack;
        String fpsStr = fps.get() ? client.getFps() + " FPS" : "";
        String pingStr = "";
        if (ping.get() && client.getConnection() != null) {
            var entry = client.getConnection().getPlayerInfo(client.player.getUUID());
            if (entry != null) pingStr = entry.getLatency() + " ms";
        }
        String clockStr = clock.get() ? CLOCK.format(LocalTime.now()) : "";
        String title = fit(track!=null ? track.title() : "", Math.max(20,layoutWidth-78), 8.5);
        double left = fpsStr.isEmpty() ? 0 : textWidth(fpsStr + "  ·  ", 8.5);
        String right = (pingStr.isEmpty() ? "" : "  ·  " + pingStr) + (clockStr.isEmpty() ? "" : "  ·  " + clockStr);
        double capsule = Math.max(160, 10 + left + 20 + 8 + textWidth(title, 8.5) + 8 + 20 + textWidth(right, 8.5) + 10);
        width = layoutWidth;
        height = 32 + 64 * expandAnim;
    }

    @Override protected void renderHud(GuiGraphicsExtractor context, Minecraft client) {
        var track=getEffectiveTrack();
        if(track==null)return;
        if(expandAnim<1)renderMediaCapsule(context,0,0,(1-expandAnim)*visualAlpha);
        if(expandAnim>0){
            var matrices=context.pose();
            matrices.pushMatrix();
            try {
                matrices.translate(0,(float)controlsOffset());
                renderMediaCard(context,0,0,expandAnim*visualAlpha);
            } finally {
                matrices.popMatrix();
            }
        }
        // The cover and title each have one draw path shared by both layouts.
        renderMediaArt(context,track,visualAlpha);
        renderMediaTitle(context,track,visualAlpha);
    }

    private double capsuleRowX(String title) {
        double titleWidth=UiRenderer.fontWidth(FontFace.GOLOS_SEMI,title,8.5);
        return Math.max(10,(width-(20+8+titleWidth+8+16))/2);
    }

    private double controlsOffset(){return (1-expandAnim)*10;}

    private void renderMediaTitle(GuiGraphicsExtractor context,WindowsMediaTracker.MediaTrack track,float alpha) {
        int a=Math.round(alpha*255);
        if(a<=5)return;
        String title=track.title();
        String compactTitle=fit(title,Math.max(20,width-78),8.5);
        double t=expandAnim;
        double startX=capsuleRowX(compactTitle)+28;
        double textX=startX+(58-startX)*t;
        double textY=11.75+.75*t;
        double fontScale=(8.5+.5*t)/9;
        double compactRoom=UiRenderer.fontWidth(FontFace.GOLOS_SEMI,compactTitle,8.5);
        double expandedRoom=Math.max(1,width-68);
        double room=Math.max(1,compactRoom+(expandedRoom-compactRoom)*t);
        double overflow=Math.max(0,UiRenderer.fontWidth(FontFace.GOLOS_SEMI,title,9)-expandedRoom);
        double phase=(System.currentTimeMillis()%7000L)/7000.0*Math.PI*2;
        double scroll=(1-Math.cos(phase))*.5*overflow*t;
        var matrices=context.pose();
        matrices.pushMatrix();
        try {
            matrices.translate((float)textX,(float)textY);
            matrices.scale((float)fontScale,(float)fontScale);
            context.enableScissor(0,-2,(int)Math.ceil(room/fontScale),16);
            try {
                // Clip one full title; the viewport and marquee blend with expansion.
                UiRenderer.font(context,FontFace.GOLOS_SEMI,title,-scroll,0,9,ColorUtil.withAlpha(0xFFFFFFFF,a));
            } finally {
                context.disableScissor();
            }
        } finally {
            matrices.popMatrix();
        }
    }

    private void renderMediaArt(GuiGraphicsExtractor context,WindowsMediaTracker.MediaTrack track,float alpha) {
        int a=Math.round(alpha*255);
        if(a<=5)return;
        double startX=capsuleRowX(fit(track.title(),Math.max(20,width-78),8.5));
        double t=expandAnim;
        double artX=startX+(10-startX)*t;
        double artY=6+4*t;
        double artSize=20+18*t;
        double radius=6-t;
        Identifier artId=MediaArtHelper.getArtTexture(track, snapshotArtworkPath);
        double artScale=artSize/38;
        var matrices=context.pose();
        matrices.pushMatrix();
        try {
            // Transform a fixed quad so movement and resizing retain subpixel precision.
            matrices.translate((float)artX,(float)artY);
            matrices.scale((float)artScale,(float)artScale);
            UiRenderer.roundedRect(context,0,0,38,38,radius/artScale,ColorUtil.withAlpha(artId!=null?0xFF0D111A:0xFF161A26,a));
            if(artId!=null){
                UiRenderer.texture(context,artId,0,0,38,38,MediaArtHelper.getTextureWidth(),MediaArtHelper.getTextureHeight(),ColorUtil.withAlpha(0xFFFFFFFF,a));
            }else{
                UiRenderer.circle(context,19,19,(3.5+1.5*t)/artScale,ColorUtil.withAlpha(0xFFFFFFFF,a));
            }
            if(t>0)UiRenderer.roundedBorder(context,0,0,38,38,radius/artScale,1/artScale,ColorUtil.withAlpha(0xFFFFFFFF,(int)(a*.2*t)));
        } finally {
            matrices.popMatrix();
        }
    }

    @Override protected boolean handleClick(double x, double y) {
        var track = WindowsMediaTracker.get().getCurrentTrack();
        if (track == null) return false;
        if (expandAnim <= 0.4f) {
            if(y<0||y>32)return false;
            WindowsMediaTracker.playPause();
            return true;
        }
        if (expandAnim < 0.9f) return false;
        y-=controlsOffset();
        if (y < 68 || y > 86) return false;
        double center = width / 2;
        if (x >= center - 46 && x <= center - 28) WindowsMediaTracker.previousTrack();
        else if (x >= center - 12 && x <= center + 12) WindowsMediaTracker.playPause();
        else if (x >= center + 24 && x <= center + 46) WindowsMediaTracker.nextTrack();
        else return false;
        return true;
    }

    private void renderMediaCapsule(GuiGraphicsExtractor context, double x, double y, float alpha) {
        int a = (int) (alpha * 255);
        if (a <= 5) return;

        boolean playing = content;
        WindowsMediaTracker.MediaTrack track = getEffectiveTrack();
        boolean hasTrack = track != null && !track.title().isBlank();
        String title = fit(hasTrack ? track.title() : "", Math.max(20,width-78), 8.5);
        Minecraft client = Minecraft.getInstance();

        double w = width;
        double h = 32;
        if(!combinedDrawing)UiRenderer.roundedRect(context, x, y, w, h, 12.0, ColorUtil.withAlpha(0xF8050608, (int) (a * opacity.get() / 100)));

        double curX=x+capsuleRowX(title);
        double textY = y + (h - 8.5) / 2.0;
        if (fps.get() && client != null) {
            String fpsStr = client.getFps() + " FPS  ·  ";
            UiRenderer.font(context, FontFace.GOLOS_SEMI, fpsStr, curX, textY, 8.5, ColorUtil.withAlpha(0xFF9CA3AF, a));
            curX += UiRenderer.fontWidth(FontFace.GOLOS_SEMI, fpsStr, 8.5);
        }
        double artSize = 20.0;
        curX += artSize + 8.0;

        curX += UiRenderer.fontWidth(FontFace.GOLOS_SEMI, title, 8.5) + 8.0;

        long now = playing?System.currentTimeMillis():lastPlayingTime;
        double barMaxH = 14.0;
        for (int i = 0; i < 4; i++) {
            double pulse = lastValidTrack!=null ? (Math.sin(now * 0.009 + i * 1.3) * 0.5 + 0.5) : 0.2;
            double barH = Math.max(3.0, Math.min(barMaxH, 3.0 + pulse * 10.0));
            double barY = y + (h - barH) / 2.0;
            UiRenderer.roundedRect(context, curX + i * 4.5, barY, 2.5, barH, 1.2, ColorUtil.withAlpha(0xFFFFFFFF, a));
        }
        curX += 20.0;
        StringBuilder rightSb = new StringBuilder();
        if (ping.get() && client != null && client.player != null && client.getConnection() != null) {
            var entry = client.getConnection().getPlayerInfo(client.player.getUUID());
            if (entry != null) rightSb.append("  ·  ").append(entry.getLatency()).append(" ms");
        }
        if (clock.get()) {
            rightSb.append("  ·  ").append(LocalTime.now().format(CLOCK));
        }
        String rightPart = rightSb.toString();
        if (!rightPart.isEmpty()) {
            UiRenderer.font(context, FontFace.GOLOS_SEMI, rightPart, curX, textY, 8.5, ColorUtil.withAlpha(0xFF9CA3AF, a));
        }
    }

    
    private void renderMediaCard(GuiGraphicsExtractor context, double x, double y, float alpha) {
        int a = (int) (alpha * 255);
        if (a <= 5) return;

        boolean playing = content;
        WindowsMediaTracker.MediaTrack track = getEffectiveTrack();
        boolean hasTrack = track != null && !track.title().isBlank();
        String artist = hasTrack ? (track.artist().isEmpty() ? track.source() : track.artist()) : "";

        double w = width;
        double h = height;
        double radius = 12.0 + (14.0 - 12.0) * expandAnim;
        if(!combinedDrawing)UiRenderer.roundedRect(context, x, y, w, h, radius, ColorUtil.withAlpha(0xF8050608, (int) (a * opacity.get() / 100)));
        if(!combinedDrawing)UiRenderer.roundedBorder(context, x, y, w, h, radius, 1.0, ColorUtil.withAlpha(0xFFFFFFFF, (int) (a * 0.14)));
        double artX = x + 10.0;
        double artY = y + 10.0;
        double artSize = 38.0;

        double textX = artX + artSize + 10.0;
        double maxTextW = w - (artSize + 24.0);
        UiRenderer.font(context, FontFace.GOLOS, fit(artist, maxTextW, 7.5), textX, artY + 16.5, 7.5, ColorUtil.withAlpha(0xFF9CA3AF, a));
        int posMs = snapshotPosition;
        int durMs = snapshotDuration;
        if (durMs <= 0) posMs = 0;

        double timeY = y + 53.0;
        String curTimeStr = formatTime(posMs);
        String durTimeStr = durMs > 0 ? formatTime(durMs) : "—";

        UiRenderer.font(context, FontFace.GOLOS, curTimeStr, x + 10.0, timeY - 2.0, 7.0, ColorUtil.withAlpha(0xFF9CA3AF, a));
        double durW = UiRenderer.fontWidth(FontFace.GOLOS, durTimeStr, 7.0);
        UiRenderer.font(context, FontFace.GOLOS, durTimeStr, x + w - 10.0 - durW, timeY - 2.0, 7.0, ColorUtil.withAlpha(0xFF9CA3AF, a));

        double trackStartX = x + 38.0;
        double trackEndX = x + w - 38.0;
        double trackW = trackEndX - trackStartX;
        double trackH = 3.5;
        double trackY = timeY + 1.0;

        float ratio = Math.max(0.0f, Math.min(1.0f, (float) posMs / Math.max(1, durMs)));
        double fillW = durMs > 0 ? Math.max(trackH, trackW * ratio) : 0;

        UiRenderer.roundedRect(context, trackStartX, trackY, trackW, trackH, 1.75, ColorUtil.withAlpha(0x444B5563, a));
        if (fillW > 0) {
            UiRenderer.roundedRect(context, trackStartX, trackY, fillW, trackH, 1.75, ColorUtil.withAlpha(0xFFFFFFFF, a));
            UiRenderer.circle(context, trackStartX + fillW, trackY + trackH / 2.0, 2.5, ColorUtil.withAlpha(0xFFFFFFFF, a));
        }
        double centerX = x + w / 2.0;
        double ctrlY = y + 68.0;
        UiRenderer.triangleLeft(context, centerX - 42.0, ctrlY + 2.0, 7.0, 11.0, ColorUtil.withAlpha(0xFFFFFFFF, a));
        UiRenderer.triangleLeft(context, centerX - 34.0, ctrlY + 2.0, 7.0, 11.0, ColorUtil.withAlpha(0xFFFFFFFF, a));
        if (playing) {
            UiRenderer.roundedRect(context, centerX - 5.5, ctrlY, 4.0, 15.0, 1.5, ColorUtil.withAlpha(0xFFFFFFFF, a));
            UiRenderer.roundedRect(context, centerX + 1.5, ctrlY, 4.0, 15.0, 1.5, ColorUtil.withAlpha(0xFFFFFFFF, a));
        } else {
            UiRenderer.triangleRight(context, centerX - 4.0, ctrlY, 11.0, 15.0, ColorUtil.withAlpha(0xFFFFFFFF, a));
        }
        UiRenderer.triangleRight(context, centerX + 27.0, ctrlY + 2.0, 7.0, 11.0, ColorUtil.withAlpha(0xFFFFFFFF, a));
        UiRenderer.triangleRight(context, centerX + 35.0, ctrlY + 2.0, 7.0, 11.0, ColorUtil.withAlpha(0xFFFFFFFF, a));
    }


    public double combinedWidth(){return width;}
    public double combinedHeight(){return height*visualAlpha;}
    public double combinedInteractionHeight(){return (expandProgress>0?96:32)*visualAlpha;}
    public float combinedAlpha(){return visualAlpha;}
    public boolean prepareCombined(boolean hover,double width){if(!isEnabled())return false;layoutWidth=width;combinedHover=hover;refresh(Minecraft.getInstance(),0);return visibility>0&&lastValidTrack!=null;}
    public void drawCombined(GuiGraphicsExtractor d,double x,double y){combinedDrawing=true;d.pose().pushMatrix();d.pose().translate((float)x,(float)(y+(1-visualAlpha)*5));d.enableScissor(0,0,(int)Math.ceil(width),(int)Math.ceil(Math.max(0,combinedHeight()-(1-visualAlpha)*5)));try{renderHud(d,Minecraft.getInstance());}finally{d.disableScissor();d.pose().popMatrix();combinedDrawing=false;}}
    public boolean clickCombined(double x,double y){return content&&visualAlpha>.8f&&handleClick(x,y);}
    private static String formatTime(int ms) {
        int total = Math.max(0, ms / 1000);
        int minutes = total / 60;
        int seconds = total % 60;
        return (minutes < 10 ? "0" : "") + minutes + (seconds < 10 ? ":0" : ":") + seconds;
    }
}
