package net.aero.visuals.mixin;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.alt.MultiAccountView;
import net.aero.visuals.module.impl.pvp.AutoClickerModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class AutoClickerInputMixin {
    @Shadow private void startUseItem() { throw new AssertionError(); }
    @Shadow private boolean startAttack() { throw new AssertionError(); }

    @Inject(method = "tick", at = @At("TAIL"))
    private void aero$attackWhileCovered(CallbackInfo ci) {
        if (!MultiAccountView.active()) return;
        var client = (Minecraft)(Object)this;
        if (client.getOverlay() != null) return;
        var module = aero$clicker();
        if (module == null) return;
        if (client.missTime > 0) client.missTime--;
        if (module.pollCoveredAttack(client)) this.startAttack();
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void aero$queueRepeats(CallbackInfo ci) {
        var module = aero$clicker();
        if (module != null) module.poll((Minecraft)(Object)this);
    }

    @Redirect(method = "pauseIfInactive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;pauseGame(Z)V"))
    private void aero$pauseOnFocusLoss(Minecraft client, boolean pauseOnly) {
        if (!AutoClickerModule.keepPlayingInBackground(client)) client.pauseGame(pauseOnly);
    }
    // The first call consumes discrete clicks; the second repeats vanilla held use.
    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startUseItem()V", ordinal = 1))
    private void aero$heldUse(Minecraft client) {
        var module = aero$clicker();
        if (module == null || !module.replacesHeldUse(client)) this.startUseItem();
    }

    @Unique
    private AutoClickerModule aero$clicker() {
        var runtime = ClientRuntime.getNullable();
        return runtime == null ? null : runtime.module(AutoClickerModule.class);
    }
}
