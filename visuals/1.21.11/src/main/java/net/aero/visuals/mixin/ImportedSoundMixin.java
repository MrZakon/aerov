package net.aero.visuals.mixin;
import net.aero.visuals.feature.ImportedFeatures;
import net.minecraft.client.sound.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(SoundSystem.class)
public abstract class ImportedSoundMixin {
    @Inject(method="stop()V", at=@At("RETURN"))
    private void aero$resetSpatialEffects(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        net.aero.visuals.feature.SpatialAudioManager.getInstance().resetContext();
    }

    @Inject(method="reloadSounds()V", at=@At("RETURN"))
    private void aero$restoreWorkerVolume(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        net.aero.visuals.alt.MultiAccountWorker.refreshMasterVolume();
    }

    @Inject(method="getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F",at=@At("RETURN"),cancellable=true)
    private void aero$volume(SoundInstance sound,CallbackInfoReturnable<Float> cir){var module=ImportedFeatures.get(ImportedFeatures.SoundController.class);if(module!=null)cir.setReturnValue(cir.getReturnValue()*module.multiplier(sound.getCategory()));var reducer=ImportedFeatures.get(net.aero.visuals.feature.premium.PremiumFeatures.SoundReducer.class);if(reducer!=null)cir.setReturnValue(cir.getReturnValue()*reducer.multiplier(sound.getId().toString()));}
}
