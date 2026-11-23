package net.aero.visuals.mixin;
import net.aero.visuals.feature.enhanced.*;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HandledScreen.class)
public abstract class ExpansionInventoryMixin {
    @Inject(method="drawSlot",at=@At("HEAD"))
    private void aero$highlight(DrawContext d,Slot slot,int mouseX,int mouseY,CallbackInfo ci){var m=VisualExpansion.get(ItemHighlighter.class);if(m!=null)m.renderSlot(d,slot);}
}
