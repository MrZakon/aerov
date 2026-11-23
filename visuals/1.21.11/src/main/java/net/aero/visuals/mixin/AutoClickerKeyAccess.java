package net.aero.visuals.mixin;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBinding.class)
public interface AutoClickerKeyAccess {
    @Accessor("timesPressed")
    int aero$getPendingClicks();

    @Accessor("timesPressed")
    void aero$setPendingClicks(int count);
}
