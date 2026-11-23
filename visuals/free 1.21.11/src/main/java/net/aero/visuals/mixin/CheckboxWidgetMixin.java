package net.aero.visuals.mixin;

import net.aero.visuals.gui.AeroMenuChrome;
import net.aero.visuals.gui.AeroVanillaChrome;
import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.CheckboxWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces square checkbox sprites with a compact rounded check control. */
@Mixin(CheckboxWidget.class)
public abstract class CheckboxWidgetMixin {
    @Inject(method = "drawIcon", at = @At("HEAD"), cancellable = true)
    private void aero$drawCheck(DrawContext context, int mouseX, int mouseY,
                                  float delta, CallbackInfo ci) {
        if (!AeroVanillaChrome.shouldTheme(MinecraftClient.getInstance().currentScreen)) return;
        CheckboxWidget widget = (CheckboxWidget) (Object) this;
        int size = Math.min(widget.getHeight(), 17);
        int x = widget.getX();
        int y = widget.getY() + Math.max(0, (widget.getHeight() - size) / 2);
        boolean checked = widget.isChecked();
        boolean hovered = widget.isHovered() || widget.isFocused();
        int accent = checked ? AeroMenuChrome.GREEN : AeroMenuChrome.VIOLET;
        UiRenderer.roundedRect(context, x, y, size, size, 5,
                checked ? ColorUtil.withAlpha(accent, 230) : 0xE617211C);
        UiRenderer.roundedBorder(context, x, y, size, size, 5, 1,
                ColorUtil.withAlpha(accent, hovered ? 195 : 85));
        if (checked) {
            UiRenderer.font(context, FontFace.GOLOS_SEMI, "✓", x + 4.2, y + 2.6,
                    10.2, AeroMenuChrome.INK);
        }
        String label = widget.getMessage().getString();
        if (label != null && !label.isBlank()) {
            double fontSize = 8.6;
            UiRenderer.font(context, FontFace.GOLOS, label, x + size + 6,
                    y + (size - fontSize * 1.2) / 2.0, fontSize,
                    widget.active ? AeroMenuChrome.TEXT : 0xFF68746E);
        }
        ci.cancel();
    }
}
