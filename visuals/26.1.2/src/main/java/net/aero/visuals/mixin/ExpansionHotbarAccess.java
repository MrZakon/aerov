package net.aero.visuals.mixin;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Gui.class)
public interface ExpansionHotbarAccess {
    @Invoker("extractHotbarAndDecorations") void aero$drawGroup(GuiGraphicsExtractor context,DeltaTracker counter);
}
