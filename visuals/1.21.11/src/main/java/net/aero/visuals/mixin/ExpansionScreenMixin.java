package net.aero.visuals.mixin;
import net.aero.visuals.feature.enhanced.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Keep the explicitly requested totem preview visible above the ClickGUI. */
@Mixin(Screen.class)
public abstract class ExpansionScreenMixin {
    @Inject(method="renderWithTooltip",at=@At("RETURN"))
    private void aero$totemPreview(DrawContext d,int mouseX,int mouseY,float delta,CallbackInfo ci){var m=VisualExpansion.get(TotemAnimation.class);if(m!=null)m.draw(d,delta);}
}
