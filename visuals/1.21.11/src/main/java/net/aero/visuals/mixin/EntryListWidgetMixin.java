package net.aero.visuals.mixin;

import net.aero.visuals.gui.AeroMenuChrome;
import net.aero.visuals.gui.AeroVanillaChrome;
import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces square native list plates and separator bands with one rounded AeroVisuals surface. */
@Mixin(EntryListWidget.class)
public abstract class EntryListWidgetMixin {
    @Inject(method = "drawMenuListBackground", at = @At("HEAD"), cancellable = true)
    private void aero$roundedList(DrawContext context, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == null || AeroVanillaChrome.isSettingsBranch(client.currentScreen)) return;
        if (!AeroVanillaChrome.shouldTheme(client.currentScreen)) return;
        ClickableWidget widget = (ClickableWidget) (Object) this;
        int inset = 0;
        AeroMenuChrome.glass(context, widget.getX(), widget.getY(), widget.getWidth(),
                widget.getHeight(), 16, 0x48FFFFFF);
        ci.cancel();
    }

    @Inject(method = "drawHeaderAndFooterSeparators", at = @At("HEAD"), cancellable = true)
    private void aero$removeSeparators(DrawContext context, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (AeroVanillaChrome.shouldTheme(client.currentScreen)) ci.cancel();
    }
}
