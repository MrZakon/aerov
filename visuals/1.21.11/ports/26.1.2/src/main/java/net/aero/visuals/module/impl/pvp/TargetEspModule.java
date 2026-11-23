package net.aero.visuals.module.impl.pvp;

import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.Render2DEvent;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.*;
import net.aero.visuals.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;


import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

public final class TargetEspModule extends Module {
    private final ColorSetting color=add(new ColorSetting("color","Цвет","Цвет маркера цели.",ColorValue.solid(0xFF5CE1F0)));
    private final DoubleSetting speed=add(new DoubleSetting("speed","Скорость","Скорость вращения эффекта.",1,.2,3,.1));
    private final DoubleSetting markerSize=add(new DoubleSetting("marker_size","Размер маркера","Размер экранного маркера Nursultan.",100,24,160,2));
    private final DoubleSetting range=add(new DoubleSetting("visual_range","Дальность наведения","Дистанция выбора видимой цели для маркера. Не меняет дальность удара.",32,4,64,1));
    private long animationStart,lastFrame,expiry;
    private int lastId=-1;private UUID lastUuid;private Object lastWorld;
    private Visual current,outgoing;
    private static final class Visual {
        final UUID id;Vec3 feet;float eye;double level;boolean clear=true;long nextRay;
        Vec3 rayFrom,rayTo;
        Visual(LivingEntity entity,float delta){id=entity.getUUID();feet=entity.getPosition(delta);eye=entity.getEyeHeight();}
        Vec3 anchor(){return feet.add(0,(eye+.4)*.5,0);}
        float alpha(){return (float)(level*level*(3-2*level));}
    }
    public TargetEspModule(){super("target_esp","TargetESP","Плавный визуальный маркер цели под прицелом или последней атакованной цели.",ModuleCategory.PVP);}
    private void reset(){lastId=-1;lastUuid=null;expiry=0;lastFrame=0;current=outgoing=null;}
    @Override protected void onEnable(){reset();lastWorld=Minecraft.getInstance().level;animationStart=System.nanoTime();}
    @Override protected void onDisable(){reset();lastWorld=null;}
    public void onAttack(Entity entity){if(!isEnabled()||!(entity instanceof LivingEntity living)||!living.isAlive())return;var world=Minecraft.getInstance().level;if(lastWorld!=world){reset();lastWorld=world;}lastId=entity.getId();lastUuid=entity.getUUID();expiry=System.nanoTime()+4_000_000_000L;}
    private LivingEntity resolve(Minecraft mc,long now){
        if(mc.hitResult instanceof EntityHitResult hit&&hit.getEntity() instanceof LivingEntity living&&living!=mc.player&&living.isAlive()&&mc.player.distanceToSqr(living)<=4096)return living;
        // Vanilla crosshairTarget stops at interaction range. This ray is visual only
        // and ends at the first block; it never changes the game's attack target.
        var eye=mc.gameRenderer.getMainCamera();
        var from=eye.position();
        var direction=new org.joml.Vector3f(0,0,-1).rotate(eye.rotation());
        var end=from.add(direction.x*range.get(),direction.y*range.get(),direction.z*range.get());
        var block=mc.level.clip(new net.minecraft.world.level.ClipContext(from,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
        if(block.getType()!=HitResult.Type.MISS)end=block.getLocation();
        var pointed=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(mc.getCameraEntity(),from,end,new AABB(from,end).inflate(1),entity->entity!=mc.player&&entity instanceof LivingEntity living&&living.isAlive()&&!entity.isSpectator()&&entity.isPickable()&&!entity.isInvisibleTo(mc.player),from.distanceToSqr(end));
        if(pointed!=null&&pointed.getEntity() instanceof LivingEntity living)return living;
        if(now<expiry&&lastId>=0){var entity=mc.level.getEntity(lastId);if(entity instanceof LivingEntity living&&living.isAlive()&&living.getUUID().equals(lastUuid)&&mc.player.distanceToSqr(living)<=4096)return living;}return null;
    }
    private boolean prepare(float delta){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.options.hideGui||(mc.screen!=null&&!(mc.screen instanceof ChatScreen))){reset();return false;}
        if(lastWorld!=mc.level){reset();lastWorld=mc.level;}
        long now=System.nanoTime();double dt=lastFrame==0?.016:Math.min(.05,(now-lastFrame)/1e9);lastFrame=now;boolean reduced=net.aero.visuals.ClientRuntime.get().settings().reducedMotion();
        var target=resolve(mc,now);
        if(target!=null){Vec3 desired=target.getPosition(delta);
            if(current==null||!current.id.equals(target.getUUID())||current.feet.distanceToSqr(desired)>64){
                if(outgoing!=null&&outgoing.id.equals(target.getUUID())&&outgoing.feet.distanceToSqr(desired)<=64){var reused=outgoing;outgoing=current;current=reused;}
                else{outgoing=current;current=new Visual(target,delta);}
            }
            current.feet=reduced?desired:current.feet.lerp(desired,1-Math.exp(-22*dt));current.eye=target.getEyeHeight();
            Vec3 from=mc.gameRenderer.getMainCamera().position(),to=current.anchor();
            // Reuse a line-of-sight result for at most 50 ms, invalidating it on meaningful movement.
            if(now>=current.nextRay||current.rayFrom==null||current.rayFrom.distanceToSqr(from)>.01||current.rayTo.distanceToSqr(to)>.01){
                var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(from,to,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));current.clear=hit.getType()==HitResult.Type.MISS||from.distanceToSqr(hit.getLocation())+.01>=from.distanceToSqr(to);current.rayFrom=from;current.rayTo=to;current.nextRay=now+50_000_000L;
            }
        }
        if(current!=null){boolean show=target!=null&&current.clear;current.level=reduced?(show?1:0):Math.clamp(current.level+(show?dt/.22:-dt/.2),0,1);if(current.level==0&&!show&&target==null)current=null;}
        if(outgoing!=null){outgoing.level=reduced?0:Math.max(0,outgoing.level-dt/.2);if(outgoing.level==0)outgoing=null;}
        return current!=null||outgoing!=null;
    }
    @Subscribe private void marker(Render2DEvent event){if(!prepare(event.tickDelta()))return;drawMarker(outgoing,event);drawMarker(current,event);}
    private void drawMarker(Visual visual,Render2DEvent event){if(visual==null||visual.alpha()<=.001f)return;var d=event.context();var point=TargetMarkerProjection.project(visual.anchor(),d.guiWidth(),d.guiHeight());if(point==null)return;float alpha=visual.alpha();int raw=color.get().eval(),tint=ColorUtil.withAlpha(raw,Math.round((raw>>>24)*alpha));double time=(System.nanoTime()-animationStart)/1e6*speed.get();NursultanMarker.draw(d,point.x,point.y,markerSize.get().floatValue(),time,tint,.88f+.12f*alpha);}
}
