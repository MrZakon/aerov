package net.aero.visuals.mixin;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.module.impl.inventory.ShulkerPreviewModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Free inventory integration only renders the selected shulker preview. */
@Mixin(HandledScreen.class)
public abstract class InventoryScreenFeaturesMixin {
    @Inject(method="render",at=@At("RETURN"))
    private void aero$preview(DrawContext context,int mouseX,int mouseY,float delta,CallbackInfo ci){
        var runtime=ClientRuntime.getNullable();if(runtime==null)return;
        var module=runtime.module(ShulkerPreviewModule.class);
        if(module!=null)module.render((HandledScreen<?>)(Object)this,context,mouseX,mouseY);
    }
}
