package net.aero.visuals.mixin;
import net.aero.visuals.feature.premium.PremiumFeatures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.ClientConnection;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerListHud.class)
public abstract class PremiumTabMixin {
    @Shadow private Text header;@Shadow private Text footer;
    @Redirect(method="render",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/hud/PlayerListHud;header:Lnet/minecraft/text/Text;",opcode=180))
    private Text aero$header(PlayerListHud self){var m=PremiumFeatures.get(PremiumFeatures.TabCustomizer.class);return m!=null&&m.isEnabled()&&!m.header.get()?null:header;}
    @Redirect(method="render",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/hud/PlayerListHud;footer:Lnet/minecraft/text/Text;",opcode=180))
    private Text aero$footer(PlayerListHud self){var m=PremiumFeatures.get(PremiumFeatures.TabCustomizer.class);return m!=null&&m.isEnabled()&&!m.footer.get()?null:footer;}
    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/MinecraftClient;isInSingleplayer()Z"))
    private boolean aero$single(MinecraftClient mc){var m=PremiumFeatures.get(PremiumFeatures.TabCustomizer.class);return m!=null&&m.isEnabled()?m.heads.get():mc.isInSingleplayer();}
    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/network/ClientConnection;isEncrypted()Z"))
    private boolean aero$heads(ClientConnection connection){var m=PremiumFeatures.get(PremiumFeatures.TabCustomizer.class);return m!=null&&m.isEnabled()?m.heads.get():connection.isEncrypted();}
    @Inject(method="renderLatencyIcon",at=@At("HEAD"),cancellable=true)
    private void aero$ping(DrawContext d,int width,int x,int y,PlayerListEntry entry,CallbackInfo ci){var m=PremiumFeatures.get(PremiumFeatures.TabCustomizer.class);if(m==null||!m.isEnabled()||!m.ping.get())return;var mc=MinecraftClient.getInstance();String value=entry.getLatency()<0?"?":String.valueOf(entry.getLatency());d.getMatrices().pushMatrix();d.getMatrices().translate(x+width-1f,y+1f);d.getMatrices().scale(.65f,.65f);d.drawText(mc.textRenderer,value,-mc.textRenderer.getWidth(value),0,entry.getLatency()>150?0xFFFF8E87:entry.getLatency()>80?0xFFF8D786:0xFFA5E5C7,false);d.getMatrices().popMatrix();ci.cancel();}
}
