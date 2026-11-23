package net.aero.visuals.mixin;

import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.aero.visuals.feature.utility.ChestTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ChestTrackerPacketsMixin {
    @Inject(method = "onInventory", at = @At("RETURN"))
    private void aero$fullContainer(InventoryS2CPacket packet, CallbackInfo ci) {
        var tracker = VisualExpansion.get(ChestTracker.class);
        if (tracker != null) tracker.contents(MinecraftClient.getInstance(), packet.syncId(), true);
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("RETURN"))
    private void aero$updatedSlot(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        var tracker = VisualExpansion.get(ChestTracker.class);
        if (tracker != null) tracker.contents(MinecraftClient.getInstance(), packet.getSyncId(), false);
    }
}
