package net.aero.visuals.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.aero.visuals.render.font.HudGlyphBatch;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphicsExtractor.class)
public abstract class HudGlyphBatchMixin {
    @WrapWithCondition(
            method = "innerBlit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuSampler;IIIIFFFFI)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addGuiElement(Lnet/minecraft/client/renderer/state/gui/GuiElementRenderState;)V"))
    private boolean aero$submitGlyph(GuiRenderState state, GuiElementRenderState element) {
        return HudGlyphBatch.shouldSubmit((GuiGraphicsExtractor)(Object)this, state, element);
    }

    @WrapWithCondition(
            method = "innerFill(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;IIIIILjava/lang/Integer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addGuiElement(Lnet/minecraft/client/renderer/state/gui/GuiElementRenderState;)V"))
    private boolean aero$submitFill(GuiRenderState state, GuiElementRenderState element) {
        return HudGlyphBatch.shouldSubmit((GuiGraphicsExtractor)(Object)this, state, element);
    }
}
