package net.aero.visuals.mixin;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.module.impl.inventory.LockSlotModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class LockSlotDropMixin {
    private LockSlotDropMixin() {}

    @Mixin(ClientPlayerEntity.class)
    public static class PlayerDrop {
        @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
        private void aero$protectSelected(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
            ClientRuntime runtime = ClientRuntime.getNullable();
            if (runtime == null) return;
            LockSlotModule module = runtime.module(LockSlotModule.class);
            if (module != null && module.protects(((ClientPlayerEntity) (Object) this).getInventory().getSelectedSlot())) cir.setReturnValue(false);
        }
    }

    @Mixin(ClientPlayerInteractionManager.class)
    public static class InventoryDrop {
        @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
        private void aero$protectInventory(int syncId, int slotId, int button, SlotActionType action, PlayerEntity player, CallbackInfo ci) {
            if (action != SlotActionType.THROW || slotId < 0 || player == null || player.currentScreenHandler == null || player.currentScreenHandler.syncId != syncId || slotId >= player.currentScreenHandler.slots.size()) return;
            ClientRuntime runtime = ClientRuntime.getNullable();
            if (runtime == null) return;
            LockSlotModule module = runtime.module(LockSlotModule.class);
            if (module != null && module.protects(player.currentScreenHandler.slots.get(slotId), player.getInventory())) ci.cancel();
        }
    }
}
