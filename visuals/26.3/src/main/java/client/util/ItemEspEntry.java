package client.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class ItemEspEntry {
   public final Matrix4f matrix4f = new Matrix4f();
   public Vec3 vec3d;
   public float value;
   public float value2;
   public ItemStack itemStack;
   public String text;
   public String text2;
   public boolean flag;
   public boolean flag2;
   public float value3;
   public float value4;
   public float value5;

   public ItemEspEntry() {
   }

   public void update() {
      this.vec3d = null;
      this.itemStack = null;
      this.text = null;
      this.text2 = null;
   }
}
