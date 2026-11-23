package net.aero.visuals.mixin;
import net.aero.visuals.feature.ImportedFeatures;
import net.minecraft.client.resources.sounds.SoundInstance;

import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(SoundEngine.class)
public abstract class ImportedSoundMixin {
    @Inject(method="calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",at=@At("RETURN"),cancellable=true)
    private void aero$volume(SoundInstance sound,CallbackInfoReturnable<Float> cir){var module=ImportedFeatures.get(ImportedFeatures.SoundController.class);if(module!=null)cir.setReturnValue(cir.getReturnValue()*module.multiplier(sound.getSource()));}
}
