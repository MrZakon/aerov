package net.aero.visuals.mixin;

import net.aero.visuals.gui.AeroMenuChrome;
import net.aero.visuals.gui.AeroVanillaChrome;
import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rounded chrome for every native text field while preserving Minecraft's editing logic. */
@Mixin(TextFieldWidget.class)
public abstract class TextFieldWidgetMixin {
    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void aero$roundedField(DrawContext context, int mouseX, int mouseY,
                                     float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!AeroVanillaChrome.shouldTheme(client.currentScreen)) return;
        // Chat owns a bottom-aligned transparent input. Skinning it like an ordinary settings
        // field stretched the control across the whole screen and created the purple bottom bar.
        if (client.currentScreen instanceof ChatScreen) return;

        TextFieldWidget widget = (TextFieldWidget) (Object) this;
        widget.setDrawsBackground(false);
        double radius = Math.min(12, Math.max(6, widget.getHeight() / 2.0));
        boolean focused = widget.isFocused();
        AeroMenuChrome.glass(context, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(),
                radius, focused ? 0xA0FFFFFF : 0x48FFFFFF);
    }
}
