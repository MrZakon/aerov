
package net.aero.visuals.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = BufferBuilder.class, priority = 900)
public abstract class QuadPositionBufferMixin implements VertexConsumer {
    @Override
    public void putBakedQuad(PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
        var direction = quad.direction().getUnitVec3f();
        Matrix4f transform = pose.pose();
        Vector3f scratch = pose.transformNormal(direction, new Vector3f());
        float normalX = scratch.x(), normalY = scratch.y(), normalZ = scratch.z();
        int emission = quad.materialInfo().lightEmission();
        // Read the four record members directly and reuse the position vector for every corner.
        aero$writeQuadVertex(transform, quad.position0(), quad.packedUV0(), instance, 0, emission, scratch, normalX, normalY, normalZ);
        aero$writeQuadVertex(transform, quad.position1(), quad.packedUV1(), instance, 1, emission, scratch, normalX, normalY, normalZ);
        aero$writeQuadVertex(transform, quad.position2(), quad.packedUV2(), instance, 2, emission, scratch, normalX, normalY, normalZ);
        aero$writeQuadVertex(transform, quad.position3(), quad.packedUV3(), instance, 3, emission, scratch, normalX, normalY, normalZ);
    }

    @Unique
    private void aero$writeQuadVertex(Matrix4f transform, Vector3fc position, long uv, QuadInstance instance,
                                      int index, int emission, Vector3f scratch, float normalX, float normalY, float normalZ) {
        int color = instance.getColor(index);
        int light = instance.getLightCoordsWithEmission(index, emission);
        transform.transformPosition(position, scratch);
        addVertex(scratch.x(), scratch.y(), scratch.z(), color, UVPair.unpackU(uv), UVPair.unpackV(uv),
                instance.overlayCoords(), light, normalX, normalY, normalZ);
    }
}

