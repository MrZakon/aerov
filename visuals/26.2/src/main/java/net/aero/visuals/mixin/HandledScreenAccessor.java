package net.aero.visuals.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {
    @Invoker("getHoveredSlot")
    Slot aero$getSlotAt(double x, double y);
}