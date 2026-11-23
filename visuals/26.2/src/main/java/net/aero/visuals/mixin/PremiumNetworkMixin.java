package net.aero.visuals.mixin;
import net.aero.visuals.feature.premium.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public abstract class PremiumNetworkMixin {
    @Inject(method = "handleDamageEvent", at = @At("RETURN"))
    private void aero$damage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        PremiumEvents.damage(packet.entityId(), packet.sourceCauseId(), packet.sourceDirectId());
    }

    @Inject(method="handleTakeItemEntity",at=@At("HEAD"))
    private void aero$pickup(ClientboundTakeItemEntityPacket p,CallbackInfo ci){if(Minecraft.getInstance().isSameThread())PremiumEvents.pickup(p.getItemId(),p.getPlayerId(),p.getAmount());}


    @Inject(method="handleEntityEvent",at=@At("HEAD"))
    private void aero$shield(ClientboundEntityEventPacket p,CallbackInfo ci){var mc=Minecraft.getInstance();if(mc.isSameThread()&&mc.level!=null&&p.getEventId()==29&&p.getEntity(mc.level)==mc.player){var m=PremiumFeatures.get(PremiumFeatures.ShieldPhysics.class);if(m!=null)m.hit();}}


}
