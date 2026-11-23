package client.render;

import net.minecraft.client.renderer.item.ItemStackRenderState;

public final class ItemRenderEntry {
   public final ItemStackRenderState itemRenderState;
   public long time;

   ItemRenderEntry(ItemStackRenderState itemRenderState2, long time2) {
      this.itemRenderState = itemRenderState2;
      this.time = time2;
   }
}
