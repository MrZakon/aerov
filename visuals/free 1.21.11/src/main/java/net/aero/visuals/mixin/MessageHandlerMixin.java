package net.aero.visuals.mixin;

import com.mojang.authlib.GameProfile;
import net.aero.visuals.user.AeroUserService;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MessageHandler.class)
public abstract class MessageHandlerMixin {
    @Inject(method = "onChatMessage", at = @At("HEAD"))
    private void aero$registerChatProfile(SignedMessage message, GameProfile sender, MessageType.Parameters params, CallbackInfo ci) {
        if (sender != null) {
            AeroUserService.registerUser(sender.name(), sender.id());
        }
    }
}
