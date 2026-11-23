package net.aero.visuals.mixin;
import net.aero.visuals.feature.enhanced.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Camera.class)
public abstract class ExpansionCameraMixin {
 @Shadow protected abstract void setPosition(Vec3 pos);
 @ModifyArg(method="alignWithEntity",at=@At(value="INVOKE",target="Lnet/minecraft/client/Camera;getMaxZoom(F)F"),index=0)
 private float aero$distance(float distance){var m=VisualExpansion.get(BetterF5.class);return m!=null&&m.isEnabled()?m.distance(distance):distance;}
 @Inject(method="update",at=@At("RETURN"))
 private void aero$height(DeltaTracker tracker,CallbackInfo ci){var m=VisualExpansion.get(BetterF5.class);if(m==null||!m.isEnabled())return;var mc=Minecraft.getInstance();if(mc.options.getCameraType().isFirstPerson()){m.reset();return;}if(mc.level==null||mc.getCameraEntity()==null)return;Vec3 from=((Camera)(Object)this).position(),to=from.add(0,m.height(),0);var hit=mc.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.getCameraEntity()));setPosition(hit.getType()==HitResult.Type.MISS?to:from.lerp(hit.getLocation(),.95));}
}
