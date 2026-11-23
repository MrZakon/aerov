package client.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record JumpCircle(Vec3 pos, long createdAt, float lifetimeMs) {
   public float getLifetimeMs() {
      return this.lifetimeMs;
   }

   public long getCreatedAt() {
      return this.createdAt;
   }

   public Vec3 getPos() {
      return this.pos;
   }

   public float getFloatByLong(long time) {
      return Mth.clamp((float)(time - this.createdAt) / this.lifetimeMs, 0.0F, 1.0F);
   }

   public boolean isLong(long time) {
      return (float)(time - this.createdAt) >= this.lifetimeMs;
   }
}
