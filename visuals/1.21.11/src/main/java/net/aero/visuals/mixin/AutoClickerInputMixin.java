package net.aero.visuals.mixin;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.alt.MultiAccountView;
import net.aero.visuals.module.impl.pvp.AutoClickerModule;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class AutoClickerInputMixin {
    @Shadow private void doItemUse() { throw new AssertionError(); }
    @Shadow private boolean doAttack() { throw new AssertionError(); }

    @Inject(method = "tick", at = @At("TAIL"))
    private void aero$attackWhileCovered(CallbackInfo ci) {
        if (!MultiAccountView.active()) return;
        var client = (MinecraftClient)(Object)this;
        if (client.getOverlay() != null) return;
        var module = aero$clicker();
        if (module == null) return;
        if (client.attackCooldown > 0) client.attackCooldown--;
        if (module.pollCoveredAttack(client)) this.doAttack();
    }

    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void aero$queueRepeats(CallbackInfo ci) {
        var module = aero$clicker();
        if (module != null) module.poll((MinecraftClient)(Object)this);
    }

    // The first call consumes discrete clicks; the second repeats vanilla held use.
    @Redirect(method = "handleInputEvents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;doItemUse()V", ordinal = 1))
    private void aero$heldUse(MinecraftClient client) {
        var module = aero$clicker();
        if (module == null || !module.replacesHeldUse(client)) this.doItemUse();
    }

    @Unique
    private AutoClickerModule aero$clicker() {
        var runtime = ClientRuntime.getNullable();
        return runtime == null ? null : runtime.module(AutoClickerModule.class);
    }
}
