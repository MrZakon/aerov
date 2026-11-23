package net.aero.visuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface ItemCooldownEntryAccessor {
    @Accessor("startTime") int aero$startTick();
    @Accessor("endTime") int aero$endTick();
}
