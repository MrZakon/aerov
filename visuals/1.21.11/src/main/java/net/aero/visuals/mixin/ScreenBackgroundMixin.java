package net.aero.visuals.mixin;

import net.aero.visuals.gui.AeroVanillaChrome;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only menu backgrounds, retaining every native Minecraft setting and control. */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void aero$renderBackground(DrawContext context, int mouseX, int mouseY,
                                         float delta, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!AeroVanillaChrome.shouldThemeBackground(screen)) return;
        AeroVanillaChrome.renderBackground(screen, context, mouseX, mouseY);
        ci.cancel();
    }

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void aero$renderPanoramaBackground(DrawContext context, float delta, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null || !AeroVanillaChrome.shouldThemeBackground(screen)) return;
        int mouseX = (int) (client.mouse.getX() * screen.width / Math.max(1, client.getWindow().getWidth()));
        int mouseY = (int) (client.mouse.getY() * screen.height / Math.max(1, client.getWindow().getHeight()));
        AeroVanillaChrome.renderBackground(screen, context, mouseX, mouseY);
        ci.cancel();
    }

    @Inject(method = "renderDarkening(Lnet/minecraft/client/gui/DrawContext;)V",
            at = @At("HEAD"), cancellable = true)
    private void aero$disableMenuTexture(DrawContext context, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (MinecraftClient.getInstance().world == null && AeroVanillaChrome.shouldThemeBackground(screen)) {
            ci.cancel();
        }
    }

    /** Prevents the one-frame vanilla blur before GameMenuScreen is replaced by AeroVisuals's pause screen. */
    @Inject(method = "applyBlur", at = @At("HEAD"), cancellable = true)
    private void aero$disableMenuBlur(DrawContext context, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (AeroVanillaChrome.shouldThemeBackground(screen)) ci.cancel();
    }

}
