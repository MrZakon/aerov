package client.api;

import client.render.ItemIconCache;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;

public record ItemStackIcon(ItemStack stack) implements Icon {
   public ItemStack getStack() {
      return this.stack;
   }

   @Override
   public void draw(float value, float value2, float value3, Matrix4f matrix4f, float value4, DrawContext drawContext) {
      if (this.stack != null && !this.stack.isEmpty()) {
         ItemIconCache.onFloatItemStackFloatFloatFloatDrawContext(value4, this.stack, value3, value, value2, drawContext);
      }
   }
}
