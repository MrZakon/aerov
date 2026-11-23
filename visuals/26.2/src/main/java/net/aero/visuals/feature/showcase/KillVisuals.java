package net.aero.visuals.feature.showcase;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.*;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.*;
import net.aero.visuals.render.*;
import net.aero.visuals.render.font.FontFace;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Random;
import org.joml.Vector2f;
/** MVisuals-inspired death visuals, driven by observed death rather than entity disappearance. */
public final class KillVisuals extends VisualExpansion.Feature implements WorldRenderable {
    public enum Mode { MATRIX, LIGHTNING, PARTICLES, RING }
    private record Effect(Vec3 position, Vec3 anchor, long born, long seed) {}
    private final EnumSetting<Mode> mode=add(new EnumSetting<>("mode","Эффект смерти","Матрица, молния, частицы или кольца.",Mode.MATRIX,Mode.class));
    private final DoubleSetting duration=number("duration","Длительность (сек.)",1.7,.5,3,.1),scale=number("size","Размер эффекта",1,.5,2,.1);
    private final ColorSetting tint=color("color","Цвет эффекта",0xFFAD8BFF);
    private final BooleanSetting overlay=flag("overlay","Экранные сканлайны",false);
    private static final int MATRIX_RGB = 0xFF6CFFAE;
    private final ArrayDeque<Effect> effects = new ArrayDeque<>();
    private final Vector2f screen = new Vector2f();
    private WeakReference<Object> world;
    public KillVisuals(){super("kill_effect","KillEffect","Локальный эффект смерти последней атакованной цели. Матрица, молния, частицы и кольца с плавным затуханием.", ModuleCategory.PVP);}
    public void death(Vec3 position) {
        var level = Minecraft.getInstance().level;
        if (!isEnabled() || level == null) return;
        if (!hasEffectsIn(level)) world = new WeakReference<>(level);
        if (effects.size() >= 8) effects.removeFirst();
        long now = System.nanoTime();
        effects.add(new Effect(position, position.add(0, 1, 0), now, now ^ position.hashCode()));
    }

    private boolean hasEffectsIn(Object level) {
        if (world != null && level != null && world.get() == level) return true;
        effects.clear();
        world = null;
        return false;
    }

    @Override protected void onDisable() {
        effects.clear();
        world = null;
    }

    @Subscribe private void tick(TickEvent.Client event) {
        if (!hasEffectsIn(event.client().level) || effects.isEmpty()) return;
        long now = System.nanoTime();
        double lifetime = duration.get() * 1e9;
        while (!effects.isEmpty() && now - effects.getFirst().born >= lifetime) effects.removeFirst();
    }

    @Override public void renderWorld(LevelRenderContext c) {
        var mc = Minecraft.getInstance();
        if (!hasEffectsIn(mc.level) || effects.isEmpty()) return;
        var m = c.poseStack();
        var buffers = WorldGeometry.buffers(c);
        if (m == null || buffers == null) return;
        var cam = mc.gameRenderer.mainCamera().position();
        Mode selected = mode.get();
        long now = System.nanoTime();
        double lifetime = duration.get() * 1e9, s = scale.get();
        int raw = selected == Mode.MATRIX ? MATRIX_RGB : tint.get().eval();
        m.pushPose();
        try {
            m.translate(-cam.x, -cam.y, -cam.z);
            var v = buffers.getBuffer(SceneGeometry.COLOR);
            for (var e : effects) {
                double t = Math.max(0, (now - e.born) / lifetime);
                if (t >= 1) continue;
                int color = ColorUtil.withAlpha(raw, (int) ((raw >>> 24) * Math.pow(1 - t, 1.5)));
                Vec3 center = e.position.add(0, .025, 0);
            if(selected==Mode.LIGHTNING){var random=new Random(e.seed);Vec3 last=center;for(int i=1;i<=10;i++){Vec3 p=center.add((random.nextDouble()-.5)*.45*s,i*.4*s,(random.nextDouble()-.5)*.45*s);SceneGeometry.beam(m.last(),v,last,p,.025*s*(1-t),color);if(i%3==0)SceneGeometry.beam(m.last(),v,p,p.add((random.nextDouble()-.5)*1.2,-.4,(random.nextDouble()-.5)*1.2),.012*s,color);last=p;}}
            else if(selected==Mode.PARTICLES){var random=new Random(e.seed);for(int i=0;i<40;i++){double angle=random.nextDouble()*Math.PI*2,r=(.15+t*2)*s,up=(random.nextDouble()*2.8*t-2.2*t*t)*s;Vec3 p=center.add(Math.cos(angle)*r,Math.max(.05,up),Math.sin(angle)*r);SceneGeometry.box(m.last(),v,p.x,p.y,p.z,.05*s*(1-t),.05*s*(1-t),.05*s*(1-t),color);}}
            else{for(int i=0;i<3;i++)SceneGeometry.ring(m.last(),v,center.add(0,i*.04,0),(.2+t*(2+i*.3))*s,.035*s,color);}
            }
        } finally {
            m.popPose();
        }
    }

    @Subscribe private void matrix(Render2DEvent event) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (!hasEffectsIn(level) || effects.isEmpty() || mode.get() != Mode.MATRIX
                || mc.player == null || net.aero.visuals.render.ClientView.hud().isHidden() || !TargetMarkerProjection.hasCurrentCapture()) return;
        long now = System.nanoTime();
        var d = event.context();
        int width = d.guiWidth(), height = d.guiHeight();
        var from = mc.gameRenderer.mainCamera().position();
        double lifetime = duration.get() * 1e9, size = scale.get();
        boolean showOverlay = overlay.get();
        double transmission = 1;
        for (var effect : effects) {
            long elapsed = now - effect.born;
            double t = Math.max(0, elapsed / lifetime);
            if (t >= 1) continue;
            var anchor = effect.anchor;
            if (!TargetMarkerProjection.projectCaptured(anchor.x, anchor.y, anchor.z, width, height, screen)) continue;
            var hit = level.clip(new net.minecraft.world.level.ClipContext(from, anchor,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
            int alpha = (int) (220 * Math.pow(1 - t, 1.4));
            int phase = (int) (elapsed / 50_000_000L);
            for (int col = 0; col < 7; col++) {
                double x = screen.x + (col - 3) * 7 * size;
                double y = screen.y + ((phase + col * 3) % 10) * size;
                long columnCode = effect.seed + col * 37L + phase / 2;
                for (int row = 0; row < 8; row++) {
                    String glyph = ((columnCode + row * 13L) & 1) == 0 ? "0" : "1";
                    UiRenderer.font(d, FontFace.PLEX_MONO, glyph, x, y + (row - 4) * 8 * size, 7 * size,
                            ColorUtil.withAlpha(MATRIX_RGB, alpha * (8 - row) / 8));
                }
            }
            if (showOverlay) transmission *= 1 - (int) (12 * (1 - t)) / 255.0;
        }
        // Same-color scanlines stack via their combined opacity.
        int lineAlpha = (int) Math.round(255 * (1 - transmission));
        if (lineAlpha != 0) {
            int color = ColorUtil.withAlpha(MATRIX_RGB, lineAlpha);
            for (int y = 0; y < height; y += 5) UiRenderer.rect(d, 0, y, width, 1, color);
        }
    }
}
