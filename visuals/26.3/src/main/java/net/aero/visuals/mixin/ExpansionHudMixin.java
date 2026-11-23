package net.aero.visuals.mixin;
import net.aero.visuals.feature.enhanced.*;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Hud.class)
public abstract class ExpansionHudMixin {
    @Inject(method="extractHotbarAndDecorations",at=@At("HEAD"),cancellable=true)
    private void aero$group(GuiGraphicsExtractor d,DeltaTracker counter,CallbackInfo ci){var m=VisualExpansion.get(VisualHud.Hotbar.class);if(m!=null&&m.replacesVanilla()&&!m.drawingGroup())ci.cancel();}
    @Inject(method="extractItemHotbar",at=@At("HEAD"),cancellable=true)
    private void aero$hotbar(GuiGraphicsExtractor d,DeltaTracker counter,CallbackInfo ci){var m=VisualExpansion.get(VisualHud.Hotbar.class);if(m!=null&&m.replacesVanilla())ci.cancel();}
    @Inject(method="extractPlayerHealth",at=@At("HEAD"),cancellable=true)
    private void aero$vitals(GuiGraphicsExtractor d,CallbackInfo ci){var m=VisualExpansion.get(VisualHud.Hotbar.class);if(m!=null&&m.replacesVitals())ci.cancel();}
}
