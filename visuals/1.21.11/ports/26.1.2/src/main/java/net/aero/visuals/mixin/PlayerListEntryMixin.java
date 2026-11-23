package net.aero.visuals.mixin;

import com.mojang.authlib.GameProfile;
import net.aero.visuals.user.AeroUserService;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the AeroVisuals mark to the left side of known players in the tab list. */
@Mixin(PlayerInfo.class)
public abstract class PlayerListEntryMixin {
    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
    private void aero$tabBadge(CallbackInfoReturnable<Component> cir) {
        PlayerInfo entry = (PlayerInfo) (Object) this;
        GameProfile profile = entry.getProfile();
        Component current = cir.getReturnValue();
        String name = profile.name();
        if (AeroUserService.isAeroUser(profile.id(), name)
                && !AeroUserService.hasBadge(current)) {
            cir.setReturnValue(AeroUserService.badge().append(current == null ? Component.literal(name) : current));
        }
    }
}
