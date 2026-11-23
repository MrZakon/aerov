package net.aero.visuals.mixin;

import net.aero.visuals.gui.AeroMenuChrome;
import net.aero.visuals.gui.AeroVanillaChrome;
import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces square native list plates and separator bands with one rounded AeroVisuals surface. */
@Mixin(AbstractSelectionList.class)
public abstract class EntryListWidgetMixin {
    @Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
    private void aero$roundedList(GuiGraphicsExtractor context, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen == null || AeroVanillaChrome.isSettingsBranch(client.screen)) return;
        if (!AeroVanillaChrome.shouldTheme(client.screen)) return;
        AbstractWidget widget = (AbstractWidget) (Object) this;
        int inset = 0;
        AeroMenuChrome.glass(context, widget.getX(), widget.getY(), widget.getWidth(),
                widget.getHeight(), 16, 0x48FFFFFF);
        ci.cancel();
    }

    @Inject(method = "extractListSeparators", at = @At("HEAD"), cancellable = true)
    private void aero$removeSeparators(GuiGraphicsExtractor context, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (AeroVanillaChrome.shouldTheme(client.screen)) ci.cancel();
    }
}
