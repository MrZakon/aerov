package client.render;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class HudRenderContext {
   private GuiGraphicsExtractor drawContext;
   private DeltaTracker renderTickCounter;

   public DeltaTracker getRenderTickCounter() {
      return this.renderTickCounter;
   }

   public GuiGraphicsExtractor getDrawContext() {
      return this.drawContext;
   }

   public HudRenderContext getHudRenderContextByDrawContextRenderTickCounter(GuiGraphicsExtractor drawContext2, DeltaTracker renderTickCounter2) {
      this.drawContext = drawContext2;
      this.renderTickCounter = renderTickCounter2;
      return this;
   }
}
