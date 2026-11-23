package net.aero.visuals.feature.enhanced;

import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.TickEvent;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.*;
import net.aero.visuals.render.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import java.util.*;
import org.joml.Vector3f;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.effect.MobEffects;

/** Bounded local particles with real block collision; Rival HitParticles visual reference. */
public final class HitParticles extends VisualExpansion.Feature implements WorldRenderable {
    private static final Identifier STAR=Identifier.fromNamespaceAndPath("aerovisuals","textures/effects/hit_star.png");
    public enum Shape { STAR, DIAMOND, SQUARE }
    public enum Physics { BOUNCE, FLY }
    private final EnumSetting<Shape> shape=add(new EnumSetting<>("shape","Форма","Форма частиц попадания.",Shape.STAR,Shape.class));
    private final EnumSetting<Physics> physics=add(new EnumSetting<>("physics","Физика","Отскок от блоков или полёт.",Physics.BOUNCE,Physics.class));
    private final DoubleSetting count=number("count","Количество",12,2,32,1),life=number("life","Длительность (сек.)",1.2,.2,3,.1),size=number("size","Размер",.08,.02,.25,.01),speed=number("speed","Скорость",.12,.03,.3,.01);
    private final ColorSetting tint=color("color","Цвет",0xFFFFBBE5);
    private final BooleanSetting critical=flag("critical","Только критические удары",false);
    private static final class Particle {Vec3 previous,position,velocity;final int born;final double phase;Particle(Vec3 p,Vec3 v,int tick,double phase){previous=position=p;velocity=v;born=tick;this.phase=phase;}}
    private final ArrayList<Particle> particles=new ArrayList<>();private Object world;private int ticks;private long generation;
    public HitParticles(){super("hit_particles","HitParticles","Частицы подтверждённого попадания: звёзды, ромбы, отскок, скорость и плавное затухание.",ModuleCategory.PVP);}
    public long captureAttack(LivingEntity entity){if(!isEnabled())return -1;var mc=Minecraft.getInstance();var p=mc.player;if(p==null||!p.hasLineOfSight(entity))return -1;boolean crit=p.getAttackStrengthScale(.5f)>.9f&&p.fallDistance>0&&!p.onGround()&&!p.isInWater()&&!p.onClimbable()&&!p.isSprinting()&&!p.isPassenger()&&!p.hasEffect(MobEffects.BLINDNESS);return !critical.get() || crit ? generation : -1;}
    private void clear(){particles.clear();ticks=0;world=null;}
    @Override protected void onEnable(){clear();world=Minecraft.getInstance().level;}
    @Override protected void onDisable(){generation++;clear();}
    @Subscribe private void tick(TickEvent.Client event){
        var mc=event.client();if(mc.level!=world){clear();world=mc.level;}if(mc.level==null||mc.player==null)return;ticks++;
        particles.removeIf(p->ticks-p.born>life.get()*20);
        for(var p:particles){p.previous=p.position;Vec3 next=p.position.add(p.velocity);if(physics.get()==Physics.BOUNCE){var hit=mc.level.clip(new ClipContext(p.position,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));if(hit.getType()!=HitResult.Type.MISS){var normal=hit.getDirection().getUnitVec3i();Vec3 n=new Vec3(normal.getX(),normal.getY(),normal.getZ());next=hit.getLocation().add(n.scale(.006));p.velocity=p.velocity.subtract(n.scale(2*p.velocity.dot(n))).scale(.55);}p.velocity=p.velocity.scale(.97).add(0,-.012,0);}else p.velocity=p.velocity.scale(.97);p.position=next;}
    }
    public void confirmedHit(LivingEntity target, long capturedGeneration) {
        if (!isEnabled() || capturedGeneration != generation) return;
        var currentWorld = Minecraft.getInstance().level;
        if (world != currentWorld) { clear(); world = currentWorld; }
        spawn(target);
    }
    private void spawn(LivingEntity target){var random=target.getRandom();for(int i=0;i<net.aero.visuals.module.impl.misc.SmartEffectsModule.particleCount(count.get().intValue(), id());i++){if(particles.size()>=192)particles.removeFirst();Vec3 p=target.position().add((random.nextDouble()-.5)*target.getBbWidth(),target.getBbHeight()*.55+(random.nextDouble()-.5)*.4,(random.nextDouble()-.5)*target.getBbWidth());double s=speed.get();particles.add(new Particle(p,new Vec3((random.nextDouble()-.5)*s,random.nextDouble()*s,(random.nextDouble()-.5)*s),ticks,random.nextDouble()*6.28));}}
    @Override public void renderWorld(LevelRenderContext c){if(c.poseStack()==null||c.bufferSource()==null)return;var mc=Minecraft.getInstance();var camera=mc.gameRenderer.getMainCamera();var cam=camera.position();float delta=mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);var m=c.poseStack();m.pushPose();try{m.translate(-cam.x,-cam.y,-cam.z);var v=c.bufferSource().getBuffer(RenderTypes.debugQuads());for(var p:particles){double age=Math.clamp((ticks+delta-p.born)/(life.get()*20),0,1),radius=size.get()*(1-age*.6);Vec3 center=p.previous.lerp(p.position,delta);int color=ColorUtil.withAlpha(tint.get().eval(),(int)((tint.get().eval()>>>24)*Math.pow(1-age,1.5)));int corners=shape.get()==Shape.STAR?8:4;double rotate=p.phase+age*2;
            if(shape.get()==Shape.STAR){var texture=c.bufferSource().getBuffer(RenderTypes.entityTranslucentEmissive(STAR));for(int i=0;i<4;i++){float x=i==0||i==3?-1:1,y=i<2?-1:1;var offset=new Vector3f(x*(float)radius,y*(float)radius,0).rotateZ((float)rotate).rotate(camera.rotation());texture.addVertex(m.last(),(float)(center.x+offset.x),(float)(center.y+offset.y),(float)(center.z+offset.z)).setColor(color).setUv(x<0?0:1,y<0?1:0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(m.last(),0,1,0);}}
            else{rotate=shape.get()==Shape.DIAMOND?0:Math.PI/4;for(int i=0;i<corners;i++){double a=rotate+i*Math.PI*2/corners,b=rotate+(i+1)*Math.PI*2/corners;var va=new Vector3f((float)(Math.cos(a)*radius),(float)(Math.sin(a)*radius),0).rotate(camera.rotation());var vb=new Vector3f((float)(Math.cos(b)*radius),(float)(Math.sin(b)*radius),0).rotate(camera.rotation());SwordTrail.quad(m,v,center,center.add(va.x,va.y,va.z),center.add(vb.x,vb.y,vb.z),center,color);}}
        }}finally{m.popPose();}}
}
