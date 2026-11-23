package net.aero.visuals.mixin;
import net.aero.visuals.feature.premium.PremiumPostFx;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.StagedVertexBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PreparedRenderType.class)
public abstract class PremiumGlintDrawMixin {
    @Inject(method="drawFromBuffer",at=@At("HEAD"),cancellable=true)
    private void aero$mask(StagedVertexBuffer.ExecuteInfo vertices,RenderPass pass,CallbackInfo ci){if(PremiumPostFx.captureGlint((PreparedRenderType)(Object)this,vertices))ci.cancel();}
}