package net.aero.visuals.mixin;

import com.mojang.authlib.GameProfile;
import net.aero.visuals.user.AeroUserService;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prepends the badge after vanilla resolves the team's formatting and spectator style. */
@Mixin(PlayerListHud.class)
public abstract class PlayerListEntryMixin {
    @Inject(method = "getPlayerName(Lnet/minecraft/client/network/PlayerListEntry;)Lnet/minecraft/text/Text;",
            at = @At("RETURN"), cancellable = true)
    private void aero$tabBadge(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        GameProfile profile = entry.getProfile();
        Text current = cir.getReturnValue();
        if (current != null && AeroUserService.isAeroUser(profile.id(), profile.name())
                && !AeroUserService.hasBadge(current)) {
            cir.setReturnValue(AeroUserService.badge().append(current));
        }
    }
}
