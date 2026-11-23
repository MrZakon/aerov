package net.aero.visuals.mixin;
import net.aero.visuals.feature.enhanced.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Camera.class)
public abstract class ExpansionCameraMixin {
    @Shadow protected abstract void setPos(Vec3d pos);
    @ModifyArg(method="update",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/Camera;clipToSpace(F)F"),index=0)
    private float aero$distance(float distance){var m=VisualExpansion.get(BetterF5.class);return m!=null&&m.isEnabled()?m.distance(distance):distance;}
    @Inject(method="update",at=@At("RETURN"))
    private void aero$height(World world,Entity focused,boolean thirdPerson,boolean inverse,float delta,CallbackInfo ci){var m=VisualExpansion.get(BetterF5.class);if(m==null||!m.isEnabled())return;if(!thirdPerson){m.reset();return;}Vec3d from=((Camera)(Object)this).getCameraPos(),to=from.add(0,m.height(),0);var hit=world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,focused));setPos(hit.getType()==HitResult.Type.MISS?to:from.lerp(hit.getPos(),.95));}
}
