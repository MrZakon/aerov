package net.aero.visuals.mixin;

import net.aero.visuals.hud.HudDragController;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class HudWidgetsChatMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void aero$widgets(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        HudDragController.renderChat(context);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void aero$saveHudPositions(CallbackInfo ci) {
        HudDragController.finish();
    }
}
