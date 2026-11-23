package net.aero.visuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Stable access to option-row geometry inherited from Minecraft's list entry. */
@Mixin(targets = "net.minecraft.client.gui.components.AbstractSelectionList$Entry")
public interface EntryListWidgetEntryAccessor {
    @Accessor("x") int aero$getX();
    @Accessor("y") int aero$getY();
    @Accessor("width") int aero$getWidth();
    @Accessor("height") int aero$getHeight();
}
