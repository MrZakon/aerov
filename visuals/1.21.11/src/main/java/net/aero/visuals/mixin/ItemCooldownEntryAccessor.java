package net.aero.visuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.entity.player.ItemCooldownManager$Entry")
public interface ItemCooldownEntryAccessor {
    @Accessor("startTick") int aero$startTick();
    @Accessor("endTick") int aero$endTick();
}
