package net.aero.visuals.mixin;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.module.impl.inventory.ItemScrollerModule;
import net.aero.visuals.module.impl.inventory.ShulkerPreviewModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class InventoryScreenFeaturesMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void aero$renderInventoryFeatures(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        ShulkerPreviewModule preview = runtime.module(ShulkerPreviewModule.class);
        if (preview != null) preview.render(screen, context, mouseX, mouseY);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void aero$itemScroller(Click click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        ItemScrollerModule scroller = runtime.module(ItemScrollerModule.class);
        if (scroller != null && scroller.drag((HandledScreen<?>) (Object) this, click.button(), click.x(), click.y())) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void aero$resetScroller(Click click, CallbackInfoReturnable<Boolean> cir) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        ItemScrollerModule scroller = runtime.module(ItemScrollerModule.class);
        if (scroller != null) scroller.reset();
    }
}
