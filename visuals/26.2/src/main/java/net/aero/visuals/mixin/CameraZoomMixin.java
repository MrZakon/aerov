package net.aero.visuals.mixin;

import net.aero.visuals.feature.ImportedFeatures;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraZoomMixin {
    @Inject(method="calculateFov",at=@At("RETURN"),cancellable=true)
    private void aero$zoom(float delta,CallbackInfoReturnable<Float> result){
        var zoom=ImportedFeatures.get(ImportedFeatures.Zoom.class);
        if(zoom!=null&&zoom.isEnabled())result.setReturnValue(Math.max(3,result.getReturnValue()*zoom.multiplier()));
    }
}
