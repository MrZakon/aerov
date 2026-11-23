package net.aero.visuals.mixin;
import net.aero.visuals.feature.showcase.ChunkAnimator;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.minecraft.client.render.WorldRenderer;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
@Mixin(WorldRenderer.class)
public abstract class ShowcaseChunkMixin {
    @ModifyArgs(method="renderBlockLayers",at=@At(value="INVOKE",target="Lnet/minecraft/client/gl/DynamicUniforms$ChunkSectionsValue;<init>(Lorg/joml/Matrix4fc;IIIFII)V"))
    private void aero$section(Args args){var module=VisualExpansion.get(ChunkAnimator.class);if(module!=null&&module.isEnabled())args.set(0,module.transform((Matrix4fc)args.get(0),(int)args.get(1),(int)args.get(2),(int)args.get(3)));}
}
