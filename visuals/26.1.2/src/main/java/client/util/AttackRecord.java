package client.util;

import net.minecraft.world.entity.player.Player;

public class AttackRecord {
   private static final long time = 5000L;
   private static Player playerEntity;
   private static long time2;

   public static void setPlayerEntity(Player playerEntity2) {
      playerEntity = playerEntity2;
      time2 = System.currentTimeMillis();
   }

   public static Player getPlayerEntity() {
      if (playerEntity == null) {
         return null;
      } else if (!playerEntity.isAlive()) {
         playerEntity = null;
         return null;
      } else if (System.currentTimeMillis() - time2 > 5000L) {
         playerEntity = null;
         return null;
      } else {
         return playerEntity;
      }
   }
}
