package net.aero.visuals.mixin;
import net.aero.visuals.feature.enhanced.*;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(InGameHud.class)
public abstract class ExpansionHudMixin {
    @Inject(method="renderMainHud",at=@At("HEAD"),cancellable=true)
    private void aero$group(DrawContext d,RenderTickCounter counter,CallbackInfo ci){var m=VisualExpansion.get(VisualHud.Hotbar.class);if(m!=null&&m.replacesVanilla()&&!m.drawingGroup())ci.cancel();}
    @Inject(method="renderHotbar",at=@At("HEAD"),cancellable=true)
    private void aero$hotbar(DrawContext d,RenderTickCounter counter,CallbackInfo ci){var m=VisualExpansion.get(VisualHud.Hotbar.class);if(m!=null&&m.replacesVanilla())ci.cancel();}
    @Inject(method="renderStatusBars",at=@At("HEAD"),cancellable=true)
    private void aero$vitals(DrawContext d,CallbackInfo ci){var m=VisualExpansion.get(VisualHud.Hotbar.class);if(m!=null&&m.replacesVitals())ci.cancel();}
}
