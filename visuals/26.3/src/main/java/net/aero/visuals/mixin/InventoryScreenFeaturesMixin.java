package net.aero.visuals.mixin;
import client.util.LegacyInput;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.module.impl.inventory.ItemScrollerModule;
import net.aero.visuals.module.impl.inventory.ShulkerPreviewModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class InventoryScreenFeaturesMixin {

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void aero$renderInventoryFeatures(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        ShulkerPreviewModule preview = runtime.module(ShulkerPreviewModule.class);
        if (preview != null) preview.render(screen, context, mouseX, mouseY);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void aero$itemScroller(MouseButtonEvent click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        ItemScrollerModule scroller = runtime.module(ItemScrollerModule.class);
        if (scroller != null && scroller.drag((AbstractContainerScreen<?>) (Object) this, LegacyInput.button(click.button()), click.x(), click.y())) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void aero$resetScroller(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        ItemScrollerModule scroller = runtime.module(ItemScrollerModule.class);
        if (scroller != null) scroller.reset();
    }
}
