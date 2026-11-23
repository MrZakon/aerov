package net.aero.visuals.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface AutoClickerKeyAccess {
    @Accessor("clickCount")
    int aero$getPendingClicks();

    @Accessor("clickCount")
    void aero$setPendingClicks(int count);
}
