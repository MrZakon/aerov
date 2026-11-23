package net.aero.visuals.mixin;
import net.aero.visuals.feature.CosmeticFeature;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerEntityRenderer.class)
public abstract class CosmeticPlayerMixin extends LivingEntityRenderer<AbstractClientPlayerEntity,PlayerEntityRenderState,PlayerEntityModel> {
    protected CosmeticPlayerMixin(EntityRendererFactory.Context context,PlayerEntityModel model,float shadow){super(context,model,shadow);}
    @Inject(method="<init>",at=@At("TAIL"))private void aero$features(EntityRendererFactory.Context context,boolean slim,CallbackInfo ci){addFeature(new CosmeticFeature((FeatureRendererContext<PlayerEntityRenderState,PlayerEntityModel>)(Object)this, context.getEquipmentModelLoader()));addFeature(new net.aero.visuals.feature.showcase.ShowcasePlayerFeature((FeatureRendererContext<PlayerEntityRenderState,PlayerEntityModel>)(Object)this));}
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void aero$faceEmotePartner(net.minecraft.entity.PlayerLikeEntity player, PlayerEntityRenderState state, float delta, CallbackInfo ci) {
        var pose = net.aero.visuals.feature.utility.PairedEmotes.playback(state.id);
        if (pose == null || state instanceof net.aero.visuals.feature.CosmeticPreviewState) return;
        state.bodyYaw = net.aero.visuals.feature.utility.PairedEmotes.face(state.bodyYaw, pose);
        state.relativeHeadYaw *= 1 - pose.weight();
        state.pitch *= 1 - pose.weight();
    }
}