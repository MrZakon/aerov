package net.aero.visuals.mixin;

import com.mojang.blaze3d.systems.VertexSorter;
import java.util.Arrays;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VertexSorter.class)
public interface TransparentQuadSortingMixin {
    @Inject(
            method = "of(Lcom/mojang/blaze3d/systems/VertexSorter$SortKeyMapper;)Lcom/mojang/blaze3d/systems/VertexSorter;",
            at = @At("RETURN"),
            cancellable = true)
    private static void aero$sortLargeMeshes(VertexSorter.SortKeyMapper distance,
                                             CallbackInfoReturnable<VertexSorter> result) {
        VertexSorter original = result.getReturnValue();
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

