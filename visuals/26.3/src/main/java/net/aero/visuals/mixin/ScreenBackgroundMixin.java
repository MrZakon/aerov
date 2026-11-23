package net.aero.visuals.mixin;

import net.aero.visuals.gui.AeroVanillaChrome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only menu backgrounds, retaining every native Minecraft setting and control. */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void aero$extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY,
                                         float delta, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!AeroVanillaChrome.shouldThemeBackground(screen)) return;
        AeroVanillaChrome.extractBackground(screen, context, mouseX, mouseY);
        ci.cancel();
    }

    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
    private void aero$renderPanoramaBackground(GuiGraphicsExtractor context, float delta, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        Minecraft client = Minecraft.getInstance();
        if (client.level != null || !AeroVanillaChrome.shouldThemeBackground(screen)) return;
        int mouseX = (int) (client.mouseHandler.xpos() * screen.width / Math.max(1, client.getWindow().getScreenWidth()));
        int mouseY = (int) (client.mouseHandler.ypos() * screen.height / Math.max(1, client.getWindow().getScreenHeight()));
        AeroVanillaChrome.extractBackground(screen, context, mouseX, mouseY);
        ci.cancel();
    }

    @Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At("HEAD"), cancellable = true)
    private void aero$disableMenuTexture(GuiGraphicsExtractor context, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (Minecraft.getInstance().level == null && AeroVanillaChrome.shouldThemeBackground(screen)) {
            ci.cancel();
        }
    }

    /** Prevents the one-frame vanilla blur before GameMenuScreen is replaced by AeroVisuals's pause screen. */
    @Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void aero$disableMenuBlur(GuiGraphicsExtractor context, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (AeroVanillaChrome.shouldThemeBackground(screen)) ci.cancel();
    }

}
