package net.aero.visuals.mixin;

import net.aero.visuals.feature.ImportedFeatures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class ImportedEmoteMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",at=@At("TAIL"))
    private void aero$pose(AvatarRenderState state,CallbackInfo ci){
        var mc=Minecraft.getInstance();var module=ImportedFeatures.get(ImportedFeatures.Emotes.class);
        if(mc.player==null||state.id!=mc.player.getId()||module==null||!module.playing())return;
        PlayerModel model=(PlayerModel)(Object)this;float t=module.time();
        switch(module.pose.get()){
            case WAVE->{model.rightArm.xRot=-2.5f;model.rightArm.zRot=-.4f-(float)Math.sin(t*8)*.3f;}
            case DANCE->{model.rightArm.zRot=-1.1f;model.leftArm.zRot=1.1f;model.rightArm.xRot=(float)Math.sin(t*5)*.7f;model.leftArm.xRot=-model.rightArm.xRot;model.body.zRot=(float)Math.sin(t*5)*.12f;model.rightLeg.xRot=(float)Math.sin(t*5)*.4f;model.leftLeg.xRot=-model.rightLeg.xRot;}
            case BOW->{model.body.xRot=.55f;model.head.xRot=.65f;model.rightArm.xRot=-.6f;model.leftArm.xRot=-.6f;}
            case CHEER->{model.rightArm.xRot=-2.8f;model.leftArm.xRot=-2.8f;model.rightArm.zRot=-.3f;model.leftArm.zRot=.3f;model.head.xRot=-.15f;}
        }
        model.rightSleeve.loadPose(model.rightArm.storePose());model.leftSleeve.loadPose(model.leftArm.storePose());model.jacket.loadPose(model.body.storePose());model.rightPants.loadPose(model.rightLeg.storePose());model.leftPants.loadPose(model.leftLeg.storePose());
    }
}
