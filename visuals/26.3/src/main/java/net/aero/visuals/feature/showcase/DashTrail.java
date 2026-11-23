package net.aero.visuals.feature.showcase;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.TickEvent;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.*;
import net.aero.visuals.render.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.*;
public final class DashTrail extends VisualExpansion.Feature implements WorldRenderable {
    public enum Style { SPARKS, SEGMENTS, GLYPHS }
    public enum Physics { DRIFT, FALL, FLY }
    private final EnumSetting<Style> style=add(new EnumSetting<>("style","Форма следа","Искры, штрихи или геометрические символы.",Style.SPARKS,Style.class));
    private final EnumSetting<Physics> physics=add(new EnumSetting<>("physics","Движение частиц","Поведение частиц после появления.",Physics.DRIFT,Physics.class));
    private final DoubleSetting count=number("count","Частиц за шаг",4,1,12,1),life=number("life","Длительность (сек.)",.7,.2,2,.1),size=number("size","Размер",.06,.02,.15,.01);
    private final ColorSetting tint=color("color","Цвет следа",0xFFA28AFF);
    private final BooleanSetting first=flag("first_person","От первого лица",false);
    private record Mote(Vec3 start,Vec3 velocity,int born,double phase){}
    private final ArrayDeque<Mote> motes=new ArrayDeque<>();private Vec3 last;private Object world;private int ticks;
    public DashTrail(){super("dash_trail","DashTrail","Светящиеся искры и штрихи за движущимся персонажем с затуханием и выбором физики.", ModuleCategory.RENDER);}
    @Override protected void onDisable(){motes.clear();last=null;world=null;ticks=0;}
    @Subscribe private void tick(TickEvent.Client event) {
        var client = event.client();
        if (client.level != world || client.player == null) {
            onDisable();
            world = client.level;
        }
        if (client.level == null || client.player == null) return;
        ticks++;
        double lifetime = life.get() * 20;
        while (!motes.isEmpty() && ticks - motes.getFirst().born > lifetime) motes.removeFirst();

        var next = client.player.position();
        var previous = last;
        last = next;
        if (previous == null) return;
        double distance = next.distanceToSqr(previous);
        if (distance <= .001 || distance >= 9) return;
        var random = client.player.getRandom();
        int particles = count.get().intValue();
        for (int index = 0; index < particles; index++) {
            if (motes.size() >= 192) motes.removeFirst();
            var position = previous.lerp(next, random.nextDouble()).add(
                    (random.nextDouble() - .5) * .4, .2 + random.nextDouble() * 1.2, (random.nextDouble() - .5) * .4);
            var velocity = new Vec3((random.nextDouble() - .5) * .025,
                    (random.nextDouble() - .5) * .025, (random.nextDouble() - .5) * .025);
            motes.add(new Mote(position, velocity, ticks, random.nextDouble() * 6.28));
        }
    }
    @Override public void renderWorld(LevelRenderContext c){var mc=Minecraft.getInstance();if(mc.player==null||c.poseStack()==null||net.aero.visuals.render.WorldGeometry.buffers(c)==null||mc.options.getCameraType().isFirstPerson()&&!first.get())return;float delta=mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);var camera=mc.gameRenderer.mainCamera();Vec3 cam=camera.position();var m=c.poseStack();m.pushPose();try{m.translate(-cam.x,-cam.y,-cam.z);var v=net.aero.visuals.render.WorldGeometry.buffers(c).getBuffer(SceneGeometry.COLOR);for(var p:motes){double age=ticks+delta-p.born,t=Math.clamp(age/(life.get()*20),0,1);Vec3 center=p.start.add(p.velocity.scale(age));if(physics.get()==Physics.FALL)center=center.add(0,-age*age*.0015,0);if(physics.get()==Physics.FLY)center=center.add(0,age*.02,0);int color=ColorUtil.withAlpha(tint.get().eval(),(int)((tint.get().eval()>>>24)*Math.pow(1-t,1.5)));double r=size.get()*(1-t*.6);
            if(style.get()==Style.SEGMENTS){SceneGeometry.beam(m.last(),v,center,center.subtract(p.velocity.scale(5)).add(0,.07,0),r*.2,color);continue;}var a=new org.joml.Vector3f((float)r,0,0).rotate(camera.rotation());var b=new org.joml.Vector3f(0,(float)r,0).rotate(camera.rotation());Vec3 right=new Vec3(a.x,a.y,a.z),up=new Vec3(b.x,b.y,b.z);if(style.get()==Style.GLYPHS){SceneGeometry.beam(m.last(),v,center.add(up),center.add(right),r*.12,color);SceneGeometry.beam(m.last(),v,center.add(right),center.subtract(up),r*.12,color);SceneGeometry.beam(m.last(),v,center.subtract(up),center.subtract(right),r*.12,color);SceneGeometry.beam(m.last(),v,center.subtract(right),center.add(up),r*.12,color);}else{SceneGeometry.quad(m.last(),v,center.subtract(right).subtract(up),center.add(right).subtract(up),center.add(right).add(up),center.subtract(right).add(up),ColorUtil.withAlpha(color,(color>>>24)/4));SceneGeometry.beam(m.last(),v,center.subtract(right.scale(.8)),center.add(right.scale(.8)),r*.1,color);SceneGeometry.beam(m.last(),v,center.subtract(up),center.add(up),r*.1,color);}
        }}finally{m.popPose();}}
}
