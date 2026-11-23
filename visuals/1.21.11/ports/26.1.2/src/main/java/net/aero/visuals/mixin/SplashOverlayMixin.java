package net.aero.visuals.mixin;

import net.aero.visuals.AeroVisuals;
import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Рисуется после Mojang-pass, сохраняя его корректную reload/fade state-machine. */
@Mixin(LoadingOverlay.class)
public abstract class SplashOverlayMixin {
    private static final Identifier LUNACY_LOGO = Identifier.fromNamespaceAndPath(AeroVisuals.MOD_ID, "textures/gui/logo.png");
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void aerovisuals$renderSplash(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta,
                                            CallbackInfo callback) {
        try {
            long now = Util.getMillis();
            float f = this.fadeOutStart > -1L ? (float)(now - this.fadeOutStart) / 1000.0F : -1.0F;
            if (f >= 2.0F) return;

            float alpha = f >= 1.0F ? (1.0F - Mth.clamp(f - 1.0F, 0.0F, 1.0F)) : 1.0F;
            if (alpha <= 0.001F) return;

            int width = context.guiWidth();
            int height = context.guiHeight();

            if (f < 1.0F) {
                context.fillGradient(0, 0, width, height, 0xFF090711, 0xFF151021);
            } else {
                int bgAlpha = (int)(255 * alpha);
                context.fillGradient(0, 0, width, height,
                        ColorUtil.withAlpha(0xFF090711, bgAlpha),
                        ColorUtil.withAlpha(0xFF151021, bgAlpha));
            }

            int logo = Math.min(126, Math.min(width, height) / 3);
            int tint = ((int)(alpha * 255) << 24) | 0x00FFFFFF;
            UiRenderer.texture(context, LUNACY_LOGO, width / 2.0 - logo / 2.0,
                    height / 2.0 - logo / 2.0, logo, logo, 128, 128, tint);
            if (f < 1.0F) {
                renderLoadingBar(context, width, height, logo);
            }
        } catch (Throwable ignored) {
        }
    }

    private void renderLoadingBar(GuiGraphicsExtractor context, int screenWidth, int screenHeight, int logoSize) {
        double barWidth = Math.min(420, Math.max(160, screenWidth * 0.38));
        double barHeight = 10;
        double x = (screenWidth - barWidth) / 2.0;
        double y = Math.min(screenHeight - 28,
                screenHeight / 2.0 + logoSize / 2.0 + 30);

        UiRenderer.shadow(context, x, y, barWidth, barHeight, barHeight / 2.0,
                ColorUtil.withAlpha(0xFF5CE1E6, 48));
        UiRenderer.roundedRect(context, x, y, barWidth, barHeight, barHeight / 2.0,
                0xE60A0E19);
        UiRenderer.roundedBorder(context, x, y, barWidth, barHeight, barHeight / 2.0, 1,
                0x665CE1E6);

        double inset = 3;
        double innerHeight = barHeight - inset * 2;
        double available = barWidth - inset * 2;
        double fillWidth = available * Math.clamp(currentProgress, 0.0F, 1.0F);
        if (fillWidth < 0.5) return;

        double fillRadius = Math.min(innerHeight / 2.0, fillWidth / 2.0);
        UiRenderer.gradientRoundedRect(context, x + inset, y + inset,
                fillWidth, innerHeight, fillRadius, 0xFF5CE1E6, 0xFFB18CFF);
        if (fillWidth > 4) {
            UiRenderer.roundedRect(context, x + inset + 2, y + inset + 0.5,
                    fillWidth - 4, 1, 0.5, 0x66FFFFFF);
        }
    }
}
