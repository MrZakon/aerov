package client.util;

import client.module.Feature;
import net.minecraft.client.multiplayer.PlayerInfo;

public class PingTracker extends Feature {
   private static final PingTracker INSTANCE = new PingTracker();
   private static int value = 0;

   public static PingTracker getInstance() {
      return INSTANCE;
   }

   public static void update() {
      value = 0;
   }

   public static int getInt() {
      if (mc != null && mc.player != null && mc.getConnection() != null) {
         PlayerInfo playerlistentry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
         if (playerlistentry != null) {
            int i = playerlistentry.getLatency();
            if (i > 0) {
               return i;
            }
         }
      }
      return value;
   }
}
