package net.aero.visuals.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.aero.visuals.render.font.HudGlyphBatch;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DrawContext.class)
public abstract class HudGlyphBatchMixin {
    @WrapWithCondition(
            method = "drawTexturedQuad(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;Lnet/minecraft/client/gl/GpuSampler;IIIIFFFFI)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/state/GuiRenderState;addSimpleElement(Lnet/minecraft/client/gui/render/state/SimpleGuiElementRenderState;)V"))
    private boolean aero$submitGlyph(GuiRenderState state, SimpleGuiElementRenderState element) {
        return HudGlyphBatch.shouldSubmit((DrawContext)(Object)this, state, element);
    }

    @WrapWithCondition(
            method = "fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/texture/TextureSetup;IIIIILjava/lang/Integer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/state/GuiRenderState;addSimpleElement(Lnet/minecraft/client/gui/render/state/SimpleGuiElementRenderState;)V"))
    private boolean aero$submitFill(GuiRenderState state, SimpleGuiElementRenderState element) {
        return HudGlyphBatch.shouldSubmit((DrawContext)(Object)this, state, element);
    }
}
