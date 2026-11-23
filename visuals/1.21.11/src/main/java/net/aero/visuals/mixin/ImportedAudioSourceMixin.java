package net.aero.visuals.mixin;
import net.aero.visuals.feature.SpatialAudioManager;
import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Source.class)
public abstract class ImportedAudioSourceMixin {
    @Shadow @Final private int pointer;
    @Unique private boolean aero$relative;
    @Unique private float aero$pitch=1;
    @Inject(method="setRelative",at=@At("HEAD"))private void aero$relative(boolean value,CallbackInfo ci){aero$relative=value;}
    @Inject(method="setPitch",at=@At("HEAD"))private void aero$pitch(float value,CallbackInfo ci){aero$pitch=value;}
    @Inject(method={"play","tick"},at=@At("HEAD"))private void aero$effects(CallbackInfo ci){var manager=SpatialAudioManager.getInstance();if(manager.enabled&&!aero$relative)manager.applySource(pointer,aero$relative,aero$pitch);else if(manager.effectsAvailable){org.lwjgl.openal.AL11.alSource3i(pointer,org.lwjgl.openal.EXTEfx.AL_AUXILIARY_SEND_FILTER,0,0,0);org.lwjgl.openal.AL10.alSourcei(pointer,org.lwjgl.openal.EXTEfx.AL_DIRECT_FILTER,0);}}
}
