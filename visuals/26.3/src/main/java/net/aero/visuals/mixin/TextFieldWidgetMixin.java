package net.aero.visuals.mixin;

import net.aero.visuals.gui.AeroMenuChrome;
import net.aero.visuals.gui.AeroVanillaChrome;
import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rounded chrome for every native text field while preserving Minecraft's editing logic. */
@Mixin(EditBox.class)
public abstract class TextFieldWidgetMixin {
    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
    private void aero$roundedField(GuiGraphicsExtractor context, int mouseX, int mouseY,
                                     float delta, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!AeroVanillaChrome.shouldTheme(client.gui.screen())) return;
        // Chat owns a bottom-aligned transparent input. Skinning it like an ordinary settings
        // field stretched the control across the whole screen and created the purple bottom bar.
        if (client.gui.screen() instanceof ChatScreen) return;

        EditBox widget = (EditBox) (Object) this;
        widget.setBordered(false);
        double radius = Math.min(12, Math.max(6, widget.getHeight() / 2.0));
        boolean focused = widget.isFocused();
        AeroMenuChrome.glass(context, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(),
                radius, focused ? 0xA0FFFFFF : 0x48FFFFFF);
    }
}
