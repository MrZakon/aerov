package net.aero.visuals.mixin;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.module.impl.inventory.LockSlotModule;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class LockSlotDropMixin {
    private LockSlotDropMixin() {}

    @Mixin(MultiPlayerGameMode.class)
    public static class PlayerDrop {
        @Inject(method = "dropItem(Lnet/minecraft/client/player/LocalPlayer;Z)V", at = @At("HEAD"), cancellable = true)
        private void aero$protectSelected(LocalPlayer player, boolean entireStack, CallbackInfo ci) {
            ClientRuntime runtime = ClientRuntime.getNullable();
            if (runtime == null) return;
            LockSlotModule module = runtime.module(LockSlotModule.class);
            if (module != null && module.protects(player.getInventory().getSelectedSlot())) ci.cancel();
        }
    }

    @Mixin(MultiPlayerGameMode.class)
    public static class InventoryDrop {
        @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
        private void aero$protectInventory(int syncId, int slotId, int button, ContainerInput action, Player player, CallbackInfo ci) {
            if (action != ContainerInput.THROW || slotId < 0 || player == null || player.containerMenu == null || player.containerMenu.containerId != syncId || slotId >= player.containerMenu.slots.size()) return;
            ClientRuntime runtime = ClientRuntime.getNullable();
            if (runtime == null) return;
            LockSlotModule module = runtime.module(LockSlotModule.class);
            if (module != null && module.protects(player.containerMenu.slots.get(slotId), player.getInventory())) ci.cancel();
        }
    }
}
