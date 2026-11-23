package net.aero.visuals.mixin;
import net.aero.visuals.feature.showcase.ChunkAnimator;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
@Mixin(LevelRenderer.class)
public abstract class ShowcaseChunkMixin {
    @ModifyArgs(method="extractSectionDrawGroups",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/DynamicGpuData$ChunkSectionInfo;<init>(IIIF)V"))
    private void aero$section(Args args){var m=VisualExpansion.get(ChunkAnimator.class);if(m!=null&&m.isEnabled())args.set(3,m.sectionAnimation(args.get(0),args.get(1),args.get(2),args.get(3)));}
}