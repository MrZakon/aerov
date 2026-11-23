package net.aero.visuals.mixin;

import net.aero.visuals.gui.AeroMenuChrome;
import net.aero.visuals.gui.AeroVanillaChrome;
import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the nine-slice button plate while preserving Minecraft's labels, icons and behavior. */
@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin {
    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
    private void aero$drawButton(DrawContext context, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!AeroVanillaChrome.shouldThemeButtons(client.currentScreen)) return;

        PressableWidget widget = (PressableWidget) (Object) this;
        double x = widget.getX();
        double y = widget.getY();
        double width = widget.getWidth();
        double height = widget.getHeight();
        double radius = Math.min(12, Math.max(5, height / 2.0));
        boolean hovered = widget.isHovered() || widget.isFocused();
        if (AeroVanillaChrome.useBlackButtons(client.currentScreen)) {
            int background = !widget.active ? 0x40000000 : hovered ? 0x80000000 : 0x58000000;
            int outline = !widget.active ? 0x18FFFFFF : hovered ? 0x5AFFFFFF : 0x30FFFFFF;
            UiRenderer.roundedRect(context, x, y, width, height, radius, background);
            UiRenderer.roundedBorder(context, x, y, width, height, radius, 1, outline);
        } else {
            AeroMenuChrome.glass(context, x, y, width, height, radius,
                    !widget.active ? 0x28FFFFFF : hovered ? 0xA0FFFFFF : 0x58FFFFFF);
            if (hovered && widget.active) {
                UiRenderer.roundedRect(context, x + 1, y + 1, width - 2, height - 2,
                        Math.max(2, radius - 1), 0x18FFFFFF);
            }
        }
        drawLabel(context, widget, hovered);
        ci.cancel();
    }

    @Inject(method = "drawLabel", at = @At("HEAD"), cancellable = true)
    private void aero$hideVanillaLabel(DrawnTextConsumer consumer, CallbackInfo ci) {
        if (AeroVanillaChrome.shouldThemeButtons(MinecraftClient.getInstance().currentScreen)) ci.cancel();
    }

    private static void drawLabel(DrawContext context, PressableWidget widget, boolean hovered) {
        String raw = widget.getMessage().getString();
        if (raw == null || raw.isBlank()) return;
        double maxWidth = Math.max(8, widget.getWidth() - 14);
        double size = Math.clamp(widget.getHeight() * 0.43, 7.4, 9.4);
        String label = fit(raw, maxWidth, size);
        double textWidth = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, label, size);
        double x = widget.getX() + (widget.getWidth() - textWidth) / 2.0;
        double y = widget.getY() + (widget.getHeight() - size * 1.20) / 2.0 - 0.3;
        int color = !widget.active ? 0xFF7B8190
                : hovered ? AeroMenuChrome.TEXT : 0xFFE2E8F4;
        UiRenderer.font(context, FontFace.GOLOS_SEMI, label, x, y, size, color);
    }

    private static String fit(String value, double maxWidth, double size) {
        return UiRenderer.fitFont(FontFace.GOLOS_SEMI, value, maxWidth, size, true);
    }
}
