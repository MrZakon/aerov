package client.api;

import client.render.ItemIconCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

public record ItemStackIcon(net.minecraft.world.item.ItemStackTemplate template) implements Icon {
   public ItemStackIcon(ItemStack stack) {
      this(stack != null && !stack.isEmpty() ? net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(stack)
         : new net.minecraft.world.item.ItemStackTemplate(net.minecraft.world.item.Items.PAPER));
   }
   public ItemStack getStack() {
      return net.minecraft.client.Minecraft.getInstance().level == null ? ItemStack.EMPTY : template.create();
   }

   @Override
   public void draw(float value, float value2, float value3, Matrix4f matrix4f, float value4, GuiGraphicsExtractor drawContext) {
      ItemStack stack=getStack();
      if (!stack.isEmpty()) {
         ItemIconCache.onFloatItemStackFloatFloatFloatDrawContext(value4, stack, value3, value, value2, drawContext);
      }
   }
}
