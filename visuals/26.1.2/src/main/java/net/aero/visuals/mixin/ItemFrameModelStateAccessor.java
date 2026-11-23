package net.aero.visuals.mixin;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockModelRenderState.class)
public interface ItemFrameModelStateAccessor {
    @Accessor("modelParts") List<BlockStateModelPart> aero$getParts();
    @Accessor("modelParts") void aero$setParts(List<BlockStateModelPart> parts);

    @Accessor("transformation") Matrix4fc aero$getTransform();
    @Accessor("transformation") void aero$setTransform(Matrix4fc transform);

    @Accessor("renderType") RenderType aero$getRenderType();
    @Accessor("renderType") void aero$setRenderType(RenderType renderType);

    @Accessor("specialRenderer") SpecialModelRenderer<?> aero$getSpecialRenderer();
    @Accessor("specialRendererTransformation") Matrix4fc aero$getSpecialTransform();
    @Accessor("tintLayers") IntList aero$getTints();
}
