package net.aero.visuals.mixin;
import net.aero.visuals.feature.ImportedFeatures;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemEntityRenderer.class)
public abstract class ImportedItemMixin {
    @Unique private record Pose(boolean ground,float yaw,float tumble){}
    @Unique private final java.util.Map<ItemEntityRenderState,Pose> aero$poses=new java.util.WeakHashMap<>();
    @Inject(method="updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",at=@At("RETURN"))
    private void aero$ground(net.minecraft.entity.ItemEntity entity,ItemEntityRenderState state,float delta,CallbackInfo ci){
        var module = ImportedFeatures.get(ImportedFeatures.ItemPhysics.class);
        if (module == null || !module.isEnabled()) return;
        aero$poses.put(state,new Pose(entity.isOnGround(),(entity.getId()*137.508f)%360,(entity.age+delta)*5));}
    @Inject(method="render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",at=@At("HEAD"),cancellable=true)
    private void aero$physics(ItemEntityRenderState state,MatrixStack matrices,OrderedRenderCommandQueue queue,CameraRenderState camera,CallbackInfo ci){
        var module=ImportedFeatures.get(ImportedFeatures.ItemPhysics.class);if(module==null||!module.isEnabled())return;ci.cancel();if(state.itemRenderState.isEmpty())return;
        var pose = aero$poses.remove(state);
        if (pose == null) pose = new Pose(false,0,state.age*5);
        var box=state.itemRenderState.getModelBoundingBox();
        var rotation=new Quaternionf().rotateY((float)Math.toRadians(pose.yaw));
        if(pose.ground){if(module.flat.get()&&box.getLengthZ()<=.1)rotation.rotateX((float)Math.PI/2);}
        else rotation.rotateX((float)Math.toRadians(pose.tumble*module.speed.get())).rotateZ((float)Math.toRadians(pose.tumble*.6*module.speed.get()));
        float bottom=Float.POSITIVE_INFINITY;for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++){var corner=new Vector3f((float)(x==0?box.minX:box.maxX),(float)(y==0?box.minY:box.maxY),(float)(z==0?box.minZ:box.maxZ)).rotate(rotation);bottom=Math.min(bottom,corner.y);}
        matrices.push();matrices.translate(0,-bottom+.015,0);matrices.multiply(rotation);
        // Stable offsets for additional copies; no vanilla bob or age-based yaw on the ground.
        int amount=Math.min(state.renderedAmount,8);for(int i=0;i<amount;i++){matrices.push();if(i>0)matrices.translate((i%3-1)*.025,i*.025,(i%2)*.025);state.itemRenderState.render(matrices,queue,state.light,net.minecraft.client.render.OverlayTexture.DEFAULT_UV,state.outlineColor);matrices.pop();}matrices.pop();
    }
}
