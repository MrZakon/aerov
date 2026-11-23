package net.aero.visuals.mixin;
import net.aero.visuals.feature.CosmeticFeature;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AvatarRenderer.class)
public abstract class CosmeticPlayerMixin extends LivingEntityRenderer<AbstractClientPlayer,AvatarRenderState,PlayerModel> {
    protected CosmeticPlayerMixin(EntityRendererProvider.Context context,PlayerModel model,float shadow){super(context,model,shadow);}
    @Inject(method="<init>",at=@At("TAIL"))private void aero$features(EntityRendererProvider.Context context,boolean slim,CallbackInfo ci){addLayer(new CosmeticFeature((RenderLayerParent<AvatarRenderState,PlayerModel>)(Object)this));}
}
