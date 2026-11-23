package net.aero.visuals.mixin;

import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.Arrays;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VertexSorting.class)
public interface TransparentQuadSortingMixin {
    @Inject(
            method = "byDistance(Lcom/mojang/blaze3d/vertex/VertexSorting$DistanceFunction;)Lcom/mojang/blaze3d/vertex/VertexSorting;",
            at = @At("RETURN"),
            cancellable = true)
    private static void aero$sortLargeMeshes(VertexSorting.DistanceFunction distance,
                                             CallbackInfoReturnable<VertexSorting> result) {
        VertexSorting original = result.getReturnValue();
        result.setReturnValue(centroids -> {
            int size = centroids.size();
            if (size < 96) return original.sort(centroids);

            Vector3f position = new Vector3f();
            long[] keys = new long[size];
            for (int index = 0; index < size; index++) {
                int bits = Float.floatToIntBits(distance.apply(centroids.get(index, position)));
                int ascending = bits ^ ((bits >> 31) & Integer.MAX_VALUE);
                // Reverse Float.compare order. The low bits preserve the original order of ties.
                keys[index] = ((long) ~ascending << 32) | index;
            }
            Arrays.sort(keys);

            int[] order = new int[size];
            for (int index = 0; index < size; index++) {
                order[index] = (int) keys[index];
            }
            return order;
        });
    }
}

