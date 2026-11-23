package client.util;

import client.module.Feature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.AABB;

public final class BlockChecks {
   private BlockChecks() {
   }

   public static boolean isBlockPos(BlockPos blockPos) {
      if (Feature.mc.level == null || Feature.mc.player == null) {
         return false;
      } else {
         return !Feature.mc.level.getBlockState(blockPos).canBeReplaced() ? false : !Feature.mc.player.getBoundingBox().intersects(new AABB(blockPos));
      }
   }

   public static boolean isBlockPos2(BlockPos blockPos) {
      return Feature.mc.level == null ? false : (Integer)Feature.mc.level.getBlockState(blockPos).getValue(RespawnAnchorBlock.CHARGE) > 0;
   }
}
