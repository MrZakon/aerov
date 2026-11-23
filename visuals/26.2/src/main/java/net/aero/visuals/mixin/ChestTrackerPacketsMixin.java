package net.aero.visuals.mixin;

import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.aero.visuals.feature.utility.ChestTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ChestTrackerPacketsMixin {
    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void aero$fullContainer(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        var tracker = VisualExpansion.get(ChestTracker.class);
        if (tracker != null) tracker.contents(Minecraft.getInstance(), packet.containerId(), true);
    }

    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void aero$updatedSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        var tracker = VisualExpansion.get(ChestTracker.class);
        if (tracker != null) tracker.contents(Minecraft.getInstance(), packet.getContainerId(), false);
    }
}
