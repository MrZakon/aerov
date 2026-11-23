package net.aero.visuals.mixin;

import com.mojang.authlib.GameProfile;
import net.aero.visuals.user.AeroUserService;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prepends the badge after vanilla resolves the team's formatting and spectator style. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListEntryMixin {
    @Inject(method = "getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;",
            at = @At("RETURN"), cancellable = true)
    private void aero$tabBadge(PlayerInfo entry, CallbackInfoReturnable<Component> cir) {
        GameProfile profile = entry.getProfile();
        Component current = cir.getReturnValue();
        if (current != null && AeroUserService.isAeroUser(profile.id(), profile.name())
                && !AeroUserService.hasBadge(current)) {
            cir.setReturnValue(AeroUserService.badge().append(current));
        }
    }
}
