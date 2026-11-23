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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class LockSlotDropMixin {
    private LockSlotDropMixin() {}

    @Mixin(LocalPlayer.class)
    public static class PlayerDrop {
        @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
        private void aero$protectSelected(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
            ClientRuntime runtime = ClientRuntime.getNullable();
            if (runtime == null) return;
            LockSlotModule module = runtime.module(LockSlotModule.class);
            if (module != null && module.protects(((LocalPlayer) (Object) this).getInventory().getSelectedSlot())) cir.setReturnValue(false);
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
