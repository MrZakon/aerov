package net.aero.visuals.mixin;
import net.aero.visuals.feature.showcase.AspectRatio;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(GameRenderer.class)
public abstract class ShowcaseProjectionMixin {
    @Inject(method="getBasicProjectionMatrix",at=@At("RETURN"))
    private void aero$aspect(float fov,CallbackInfoReturnable<Matrix4f> cir){var mc=MinecraftClient.getInstance();var module=VisualExpansion.get(AspectRatio.class);if(module==null||!module.isEnabled()||mc.world==null||mc.player==null)return;float normal=(float)mc.getWindow().getFramebufferWidth()/Math.max(1,mc.getWindow().getFramebufferHeight());var matrix=cir.getReturnValue();matrix.m00(matrix.m00()*normal/module.ratio());}
}
