package net.aero.visuals.mixin;

import com.mojang.authlib.GameProfile;
import net.aero.visuals.user.AeroUserService;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatListener.class)
public abstract class MessageHandlerMixin {
    @Inject(method = "handlePlayerChatMessage", at = @At("HEAD"))
    private void aero$registerChatProfile(PlayerChatMessage message, GameProfile sender, ChatType.Bound params, CallbackInfo ci) {
        if (sender != null) {
            AeroUserService.registerUser(sender.name(), sender.id());
        }
    }
}
