package client.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public record BlockPlacement(BlockPos pos, Direction side, Vec3 aimPoint) {
   public Direction getSide() {
      return this.side;
   }

   public Vec3 getAimPoint() {
      return this.aimPoint;
   }

   public BlockPos getPos() {
      return this.pos;
   }
}
