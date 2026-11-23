package net.aero.visuals.mixin;

import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

@Mixin(ItemCooldownManager.class)
public interface ItemCooldownManagerAccessor {
    @Accessor("entries") Map<Identifier, ?> aero$entries();
    @Accessor("tick") int aero$tick();
}
