package net.aero.visuals.mixin;

import net.aero.visuals.command.ClientCommandHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void aero$onSendChatMessage(String content, CallbackInfo ci) {
        if (ClientCommandHandler.handle(content)) {
            ci.cancel();
        }
    }
}
