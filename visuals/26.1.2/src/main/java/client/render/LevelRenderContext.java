package client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;

public class LevelRenderContext {
   private PoseStack matrixStack;
   private Camera camera;
   private DeltaTracker renderTickCounter;
   private ClientLevel clientWorld;

   public LevelRenderContext() {
   }

   public LevelRenderContext(PoseStack matrixStack, Camera camera, DeltaTracker renderTickCounter, ClientLevel clientWorld) {
      this.getLevelRenderContextByMatrixStackCameraRenderTickCounterClientWorld(matrixStack, camera, renderTickCounter, clientWorld);
   }

   public DeltaTracker getRenderTickCounter() {
      return this.renderTickCounter;
   }

   public Camera getCamera() {
      return this.camera;
   }

   public ClientLevel getClientWorld() {
      return this.clientWorld;
   }

   public PoseStack getMatrixStack() {
      return this.matrixStack;
   }

   public LevelRenderContext getLevelRenderContextByMatrixStackCameraRenderTickCounterClientWorld(
      PoseStack matrixStack2, Camera camera2, DeltaTracker renderTickCounter2, ClientLevel clientWorld2
   ) {
      this.matrixStack = matrixStack2;
      this.camera = camera2;
      this.renderTickCounter = renderTickCounter2;
      this.clientWorld = clientWorld2;
      return this;
   }
}
