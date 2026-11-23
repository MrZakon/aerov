package client.util;

import client.module.Feature;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class RaycastUtil {
   public static boolean isVec3dVec3d(Vec3 vec3d, Vec3 vec3d2) {
      if (Feature.mc.level == null) {
         return false;
      } else if (vec3d2.distanceToSqr(vec3d) < 1.0E-4) {
         return false;
      } else {
         ClipContext raycastcontext = new ClipContext(vec3d2, vec3d, Block.COLLIDER, Fluid.NONE, Feature.mc.player);
         BlockHitResult blockhitresult = Feature.mc.level.clip(raycastcontext);
         return blockhitresult.getType() != Type.MISS;
      }
   }
}
