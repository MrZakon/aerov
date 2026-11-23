package client.util;

import client.enums.ServerMode;
import client.module.Feature;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;

public class HealthTracker {
   private static final Map<UUID, Float> map = new HashMap<>();
   private static long time = 0L;

   private HealthTracker() {
   }

   public static void update() {
      map.clear();
      time = 0L;
   }

   public static String getStringByFloat(float value) {
      return isFloat(value) ? "?" : Integer.toString(Math.round(value));
   }

   private static float getFloatByPlayerEntity(Player playerEntity) {
      try {
         Level world = playerEntity.level();
         if (world == null) {
            return -1.0F;
         }

         Scoreboard scoreboard = world.getScoreboard();
         if (scoreboard == null) {
            return -1.0F;
         }

         Objective scoreboardobjective = scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME);
         if (scoreboardobjective == null) {
            return -1.0F;
         }

         ReadOnlyScoreInfo readablescoreboardscore = scoreboard.getPlayerScoreInfo(playerEntity, scoreboardobjective);
         if (readablescoreboardscore != null) {
            return readablescoreboardscore.value();
         }
      } catch (Exception exception) {
      }

      return -1.0F;
   }

   public static float getFloatByFloatFloat(float value, float value2) {
      return !isFloat(value2) && !(value <= 0.0F) ? Math.clamp(value2 / value, 0.0F, 1.0F) : 1.0F;
   }

   public static String getStringByFloatFloat(float value, float value2) {
      if (isFloat(value)) {
         return "?";
      } else {
         int i = Math.round(value / 2.0F);
         int j = Math.round(value2 / 2.0F);
         return i + "/" + j;
      }
   }

   private static void update2() {
      long i = System.currentTimeMillis();
      if (i - time >= 100L) {
         time = i;
         ClientLevel clientworld = Feature.mc.level;
         if (clientworld != null) {
            Scoreboard scoreboard = clientworld.getScoreboard();
            if (scoreboard != null) {
               Objective scoreboardobjective = null;

               for (Objective scoreboardobjective1 : scoreboard.getObjectives()) {
                  if (scoreboardobjective1 != null) {
                     String s = scoreboardobjective1.getName().toLowerCase();
                     String s1 = scoreboardobjective1.getDisplayName().getString().toLowerCase();
                     if (s.contains("health") || s.contains("hp") || s.contains("heart") || s1.contains("♥") || s1.contains("❤") || s1.contains("health")) {
                        scoreboardobjective = scoreboardobjective1;
                        break;
                     }
                  }
               }

               if (scoreboardobjective == null) {
                  scoreboardobjective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
               }

               if (scoreboardobjective != null) {
                  for (Player playerentity : clientworld.players()) {
                     ReadOnlyScoreInfo readablescoreboardscore = scoreboard.getPlayerScoreInfo(playerentity, scoreboardobjective);
                     if (readablescoreboardscore != null) {
                        map.put(playerentity.getUUID(), (float)readablescoreboardscore.value());
                     }
                  }
               }
            }
         }
      }
   }

   public static float getFloatByServerModePlayerEntity(ServerMode serverMode, Player playerEntity) {
      float f = playerEntity.getHealth();
      float f1 = playerEntity.getAbsorptionAmount();
      if (serverMode == ServerMode.FUNTIME) {
         float f2 = getFloatByPlayerEntity(playerEntity);
         if (f2 > 0.0F) {
            return f2;
         }
      } else if (serverMode == ServerMode.MINEBLAZE) {
         update2();
         Float f3 = map.get(playerEntity.getUUID());
         if (f3 != null && f3 > 0.0F) {
            return f3 + f1;
         }
      }

      return f + f1;
   }

   public static float getFloatByPlayerEntityServerMode(Player playerEntity, ServerMode serverMode) {
      float f = playerEntity.getMaxHealth();
      float f1 = playerEntity.getAbsorptionAmount();
      if (serverMode == ServerMode.OFF) {
         return f + f1;
      } else {
         return serverMode == ServerMode.FUNTIME ? Math.max(f, 20.0F) : Math.max(f, 20.0F) + f1;
      }
   }

   public static boolean isFloat(float value) {
      return value > 100.0F;
   }

   public static boolean isPlayerEntity(Player playerEntity) {
      return getFloatByPlayerEntity(playerEntity) > 0.0F;
   }

   public static String getStringByFloat2(float value) {
      return isFloat(value) ? "?" : Math.round(value) + "HP";
   }

   public static ServerMode getServerModeByString(String text) {
      if ("FT".equals(text)) {
         return ServerMode.FUNTIME;
      } else {
         return "MB".equals(text) ? ServerMode.MINEBLAZE : ServerMode.OFF;
      }
   }
}
