package client.api;

import com.mojang.blaze3d.vertex.PoseStack;

@FunctionalInterface
public interface MatrixDrawCall {
   public void apply(PoseStack matrixStack, int count, int count2, int count3, float value);
}
