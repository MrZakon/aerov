package net.aero.visuals.mixin;

import net.aero.visuals.hud.HudDragController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class HudWidgetsChatMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void aero$widgets(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        HudDragController.renderChat(context);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void aero$saveHudPositions(CallbackInfo ci) {
        HudDragController.closeChat();
    }
}
