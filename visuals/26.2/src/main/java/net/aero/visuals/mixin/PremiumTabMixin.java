package net.aero.visuals.mixin;
import net.aero.visuals.feature.premium.PremiumFeatures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerTabOverlay.class)
public abstract class PremiumTabMixin {
    @Shadow private Component header;@Shadow private Component footer;
    @Redirect(method="extractRenderState",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/components/PlayerTabOverlay;header:Lnet/minecraft/network/chat/Component;",opcode=180))
    private Component aero$header(PlayerTabOverlay self){var m=PremiumFeatures.get(PremiumFeatures.TabCustomizer.class);return m!=null&&m.isEnabled()&&!m.header.get()?null:header;}
    @Redirect(method="extractRenderState",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/components/PlayerTabOverlay;footer:Lnet/minecraft/network/chat/Component;",opcode=180))
    private Component aero$footer(PlayerTabOverlay self){var m=PremiumFeatures.get(PremiumFeatures.TabCustomizer.class);return m!=null&&m.isEnabled()&&!m.footer.get()?null:footer;}
    @Redirect(method="extractRenderState",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientPacketListener;onlineMode()Z"))
    private boolean aero$heads(net.minecraft.client.multiplayer.ClientPacketListener connection){var m=PremiumFeatures.get(PremiumFeatures.TabCustomizer.class);return m!=null&&m.isEnabled()?m.heads.get():connection.onlineMode();}
    @Inject(method="extractPingIcon",at=@At("HEAD"),cancellable=true)
    private void aero$ping(GuiGraphicsExtractor d,int width,int x,int y,PlayerInfo entry,CallbackInfo ci){var m=PremiumFeatures.get(PremiumFeatures.TabCustomizer.class);if(m==null||!m.isEnabled()||!m.ping.get())return;var mc=Minecraft.getInstance();String value=entry.getLatency()<0?"?":String.valueOf(entry.getLatency());d.pose().pushMatrix();d.pose().translate(x+width-1f,y+1f);d.pose().scale(.65f,.65f);d.text(mc.font,value,-mc.font.width(value),0,entry.getLatency()>150?0xFFFF8E87:entry.getLatency()>80?0xFFF8D786:0xFFA5E5C7,false);d.pose().popMatrix();ci.cancel();}
}
