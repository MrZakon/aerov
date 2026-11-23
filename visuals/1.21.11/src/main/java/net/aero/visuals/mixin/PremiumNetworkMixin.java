package net.aero.visuals.mixin;
import net.aero.visuals.feature.premium.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPlayNetworkHandler.class)
public abstract class PremiumNetworkMixin {
    @Inject(method = "onEntityDamage", at = @At("RETURN"))
    private void aero$damage(EntityDamageS2CPacket packet, CallbackInfo ci) {
        PremiumEvents.damage(packet.entityId(), packet.sourceCauseId(), packet.sourceDirectId());
    }

    @Inject(method="onItemPickupAnimation",at=@At("HEAD"))
    private void aero$pickup(ItemPickupAnimationS2CPacket p,CallbackInfo ci){if(MinecraftClient.getInstance().isOnThread())PremiumEvents.pickup(p.getEntityId(),p.getCollectorEntityId(),p.getStackAmount());}


    @Inject(method="onEntityStatus",at=@At("HEAD"))
    private void aero$shield(EntityStatusS2CPacket p,CallbackInfo ci){var mc=MinecraftClient.getInstance();if(mc.isOnThread()&&mc.world!=null&&p.getStatus()==29&&p.getEntity(mc.world)==mc.player){var m=PremiumFeatures.get(PremiumFeatures.ShieldPhysics.class);if(m!=null)m.hit();}}


}
