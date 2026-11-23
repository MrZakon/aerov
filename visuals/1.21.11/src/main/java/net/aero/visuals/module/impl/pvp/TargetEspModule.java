package net.aero.visuals.module.impl.pvp;

import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.Render2DEvent;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.*;
import net.aero.visuals.render.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import java.util.UUID;

public final class TargetEspModule extends Module {
    private final ColorSetting color=add(new ColorSetting("color","Цвет","Цвет маркера цели.",ColorValue.solid(0xFF5CE1F0)));
    private final DoubleSetting speed=add(new DoubleSetting("speed","Скорость","Скорость вращения эффекта.",1,.2,3,.1));
    private final DoubleSetting markerSize=add(new DoubleSetting("marker_size","Размер маркера","Размер экранного маркера Nursultan.",100,24,160,2));
    private final DoubleSetting range=add(new DoubleSetting("visual_range","Дальность наведения","Дистанция выбора видимой цели для маркера. Не меняет дальность удара.",32,4,64,1));
    private long animationStart,lastFrame;
    private final VisualTarget attack = new VisualTarget();
    private java.lang.ref.WeakReference<Object> lastWorld = new java.lang.ref.WeakReference<>(null);
    private Visual current,outgoing;
    private static final class Visual {
        final UUID id;Vec3d feet;float eye;double level;boolean clear=true;long nextRay;
        Vec3d rayFrom,rayTo;
        Visual(LivingEntity entity,float delta){id=entity.getUuid();feet=entity.getLerpedPos(delta);eye=entity.getStandingEyeHeight();}
        Vec3d anchor(){return feet.add(0,(eye+.4)*.5,0);}
        float alpha(){return (float)(level*level*(3-2*level));}
    }
    public TargetEspModule(){super("target_esp","TargetESP","Плавный визуальный маркер цели под прицелом или последней атакованной цели.",ModuleCategory.PVP);}
    private void reset(){attack.clear();lastFrame=0;current=outgoing=null;}
    @Override protected void onEnable(){reset();lastWorld = new java.lang.ref.WeakReference<>(MinecraftClient.getInstance().world);animationStart=System.nanoTime();}
    @Override protected void onDisable(){reset();lastWorld.clear();}
    public void onAttack(Entity entity){if(!isEnabled()||!(entity instanceof LivingEntity living)||!living.isAlive())return;var world=MinecraftClient.getInstance().world;if(lastWorld.get()!=world){reset();lastWorld = new java.lang.ref.WeakReference<>(world);}attack.remember(MinecraftClient.getInstance(), living, 4_000_000_000L);}
    private LivingEntity resolve(MinecraftClient mc,long now){
        var crosshair = VisualTarget.underCrosshair(mc);
        if (crosshair != null && mc.player.squaredDistanceTo(crosshair) <= 4096) return crosshair;
        // Vanilla crosshairTarget stops at interaction range. This ray is visual only
        // and ends at the first block; it never changes the game's attack target.
        var eye=mc.gameRenderer.getCamera();
        var from=eye.getCameraPos();
        var direction=new org.joml.Vector3f(0,0,-1).rotate(eye.getRotation());
        var end=from.add(direction.x*range.get(),direction.y*range.get(),direction.z*range.get());
        var block=mc.world.raycast(new net.minecraft.world.RaycastContext(from,end,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,mc.player));
        if(block.getType()!=HitResult.Type.MISS)end=block.getPos();
        var pointed=net.minecraft.entity.projectile.ProjectileUtil.raycast(mc.getCameraEntity(),from,end,new Box(from,end).expand(1),entity->entity!=mc.player&&entity instanceof LivingEntity living&&living.isAlive()&&!entity.isSpectator()&&entity.canHit()&&!entity.isInvisibleTo(mc.player),from.squaredDistanceTo(end));
        if(pointed!=null&&pointed.getEntity() instanceof LivingEntity living)return living;
        var previous = attack.lastAttacked(mc, now);
        return previous != null && mc.player.squaredDistanceTo(previous) <= 4096 ? previous : null;
    }
    private boolean prepare(float delta){
        var mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null||mc.options.hudHidden||(mc.currentScreen!=null&&!(mc.currentScreen instanceof ChatScreen))){reset();return false;}
        if(lastWorld.get()!=mc.world){reset();lastWorld = new java.lang.ref.WeakReference<>(mc.world);}
        long now=System.nanoTime();double dt=lastFrame==0?.016:Math.min(.05,(now-lastFrame)/1e9);lastFrame=now;boolean reduced=net.aero.visuals.ClientRuntime.get().settings().reducedMotion();
        var target=resolve(mc,now);
        if(target!=null){Vec3d desired=target.getLerpedPos(delta);
            if(current==null||!current.id.equals(target.getUuid())||current.feet.squaredDistanceTo(desired)>64){
                if(outgoing!=null&&outgoing.id.equals(target.getUuid())&&outgoing.feet.squaredDistanceTo(desired)<=64){var reused=outgoing;outgoing=current;current=reused;}
                else{outgoing=current;current=new Visual(target,delta);}
            }
            current.feet=reduced?desired:current.feet.lerp(desired,1-Math.exp(-22*dt));current.eye=target.getStandingEyeHeight();
            Vec3d from=mc.gameRenderer.getCamera().getCameraPos(),to=current.anchor();
            // Reuse a line-of-sight result for at most 50 ms, invalidating it on meaningful movement.
            if(now>=current.nextRay||current.rayFrom==null||current.rayFrom.squaredDistanceTo(from)>.01||current.rayTo.squaredDistanceTo(to)>.01){
                var hit=mc.world.raycast(new net.minecraft.world.RaycastContext(from,to,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,mc.player));current.clear=hit.getType()==HitResult.Type.MISS||from.squaredDistanceTo(hit.getPos())+.01>=from.squaredDistanceTo(to);current.rayFrom=from;current.rayTo=to;current.nextRay=now+50_000_000L;
            }
        }
        if(current!=null){boolean show=target!=null&&current.clear;current.level=reduced?(show?1:0):Math.clamp(current.level+(show?dt/.22:-dt/.2),0,1);if(current.level==0&&!show&&target==null)current=null;}
        if(outgoing!=null){outgoing.level=reduced?0:Math.max(0,outgoing.level-dt/.2);if(outgoing.level==0)outgoing=null;}
        return current!=null||outgoing!=null;
    }
    @Subscribe private void marker(Render2DEvent event){if(!prepare(event.tickDelta()))return;drawMarker(outgoing,event);drawMarker(current,event);}
    private void drawMarker(Visual visual,Render2DEvent event){if(visual==null||visual.alpha()<=.001f)return;var d=event.context();var point=TargetMarkerProjection.project(visual.anchor(),d.getScaledWindowWidth(),d.getScaledWindowHeight());if(point==null)return;float alpha=visual.alpha();int raw=color.get().eval(),tint=ColorUtil.withAlpha(raw,Math.round((raw>>>24)*alpha));double time=(System.nanoTime()-animationStart)/1e6*speed.get();NursultanMarker.draw(d,point.x,point.y,markerSize.get().floatValue(),time,tint,.88f+.12f*alpha);}
}
