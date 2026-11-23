package net.aero.visuals.mixin;

import net.aero.visuals.feature.ImportedFeatures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class ImportedEmoteMixin {
    @Inject(method="setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",at=@At("TAIL"))
    private void aero$pose(PlayerEntityRenderState state,CallbackInfo ci){
        var pair = net.aero.visuals.feature.utility.PairedEmotes.playback(state.id);
        if (pair != null && !(state instanceof net.aero.visuals.feature.CosmeticPreviewState)) {
            aero$pairedPose((PlayerEntityModel) (Object) this, pair);
            return;
        }
        var mc=MinecraftClient.getInstance();var module=ImportedFeatures.get(ImportedFeatures.Emotes.class);
        if(mc.player==null||state.id!=mc.player.getId()||module==null||!module.playing())return;
        PlayerEntityModel model=(PlayerEntityModel)(Object)this;float t=module.time();
        switch(module.pose.get()){
            case WAVE->{model.rightArm.pitch=-2.5f;model.rightArm.roll=-.4f-(float)Math.sin(t*8)*.3f;}
            case DANCE->{model.rightArm.roll=-1.1f;model.leftArm.roll=1.1f;model.rightArm.pitch=(float)Math.sin(t*5)*.7f;model.leftArm.pitch=-model.rightArm.pitch;model.body.roll=(float)Math.sin(t*5)*.12f;model.rightLeg.pitch=(float)Math.sin(t*5)*.4f;model.leftLeg.pitch=-model.rightLeg.pitch;}
            case BOW->{model.body.pitch=.55f;model.head.pitch=.65f;model.rightArm.pitch=-.6f;model.leftArm.pitch=-.6f;}
            case CHEER->{model.rightArm.pitch=-2.8f;model.leftArm.pitch=-2.8f;model.rightArm.roll=-.3f;model.leftArm.roll=.3f;model.head.pitch=-.15f;}
        }
        model.rightSleeve.setTransform(model.rightArm.getTransform());model.leftSleeve.setTransform(model.leftArm.getTransform());model.jacket.setTransform(model.body.getTransform());model.rightPants.setTransform(model.rightLeg.getTransform());model.leftPants.setTransform(model.leftLeg.getTransform());
    }
    @org.spongepowered.asm.mixin.Unique
    private static void aero$pairedPose(PlayerEntityModel model, net.aero.visuals.feature.utility.PairedEmotes.Playback pair) {
        float weight = pair.weight();
        float sway = pair.sway();
        switch (pair.pose()) {
            case HIGH_FIVE -> {
                var raised = pair.second() ? model.leftArm : model.rightArm;
                float contact = Math.max(0, 1 - Math.abs(pair.seconds() - 1.65f) * 2.8f);
                raised.pitch += (-2.68f + contact * .78f - raised.pitch) * weight;
                raised.roll += ((pair.second() ? -.18f : .18f) - raised.roll) * weight;
                model.body.pitch += contact * .14f * weight;
            }
            case DANCE -> {
                model.rightArm.roll += (-1.2f - model.rightArm.roll) * weight;
                model.leftArm.roll += (1.2f - model.leftArm.roll) * weight;
                model.rightArm.pitch += sway * .83f * weight;
                model.leftArm.pitch -= sway * .83f * weight;
                model.rightLeg.pitch -= sway * .26f * weight;
                model.leftLeg.pitch += sway * .26f * weight;
                model.body.roll += sway * .19f * weight;
            }
            case DUEL -> {
                model.rightArm.pitch += ((pair.second() ? -1.02f : -1.62f) - model.rightArm.pitch) * weight;
                model.leftArm.pitch += ((pair.second() ? -1.62f : -1.02f) - model.leftArm.pitch) * weight;
                model.rightArm.roll -= .23f * weight;
                model.leftArm.roll += .23f * weight;
                model.body.pitch += (.17f + sway * .03f) * weight;
                model.rightLeg.pitch += (pair.second() ? .27f : -.27f) * weight;
                model.leftLeg.pitch += (pair.second() ? -.27f : .27f) * weight;
            }
        }
        model.rightSleeve.setTransform(model.rightArm.getTransform());model.leftSleeve.setTransform(model.leftArm.getTransform());model.jacket.setTransform(model.body.getTransform());model.rightPants.setTransform(model.rightLeg.getTransform());model.leftPants.setTransform(model.leftLeg.getTransform());
    }
}