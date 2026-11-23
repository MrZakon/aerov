package net.aero.visuals.mixin;
import net.aero.visuals.feature.enhanced.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractContainerScreen.class)
public abstract class ExpansionInventoryMixin {
    @Inject(method="extractSlot",at=@At("HEAD"))
    private void aero$highlight(GuiGraphicsExtractor d,Slot slot,int mouseX,int mouseY,CallbackInfo ci){var m=VisualExpansion.get(ItemHighlighter.class);if(m!=null)m.renderSlot(d,slot);}
}
