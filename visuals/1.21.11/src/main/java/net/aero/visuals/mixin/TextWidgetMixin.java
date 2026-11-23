package net.aero.visuals.mixin;

import net.aero.visuals.gui.AeroMenuChrome;
import net.aero.visuals.gui.AeroVanillaChrome;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AbstractTextWidget;
import net.minecraft.client.gui.widget.TextWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses the bundled Golos face for native one-line menu titles and captions. */
@Mixin(AbstractTextWidget.class)
public abstract class TextWidgetMixin {
    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void aero$renderCleanText(DrawContext context, int mouseX, int mouseY,
                                        float delta, CallbackInfo ci) {
        if (!AeroVanillaChrome.shouldTheme(MinecraftClient.getInstance().currentScreen)) return;
        if (!((Object) this instanceof TextWidget widget)) return;

        String value = widget.getMessage().getString();
        if (value == null || value.isBlank()) {
            ci.cancel();
            return;
        }
        double size = 9.0;
        double available = Math.max(1, widget.getWidth());
        double natural = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, value, size);
        if (natural > available) size = Math.max(7.2, size * available / natural);
        double textWidth = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, value, size);
        double x = widget.getX() + (widget.getWidth() - textWidth) / 2.0;
        double y = widget.getY() + (widget.getHeight() - size * 1.2) / 2.0;
        UiRenderer.font(context, FontFace.GOLOS_SEMI, value, x, y, size,
                widget.isHovered() ? AeroMenuChrome.GREEN : AeroMenuChrome.TEXT);
        ci.cancel();
    }
}
