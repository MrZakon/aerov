package client.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class DroppedItemEntry {
   public final ItemStack itemStack;
   public Vec3 vec3d;

   public DroppedItemEntry(ItemStack itemStack2, Vec3 vec3d2) {
      this.itemStack = itemStack2;
      this.vec3d = vec3d2;
   }
}
