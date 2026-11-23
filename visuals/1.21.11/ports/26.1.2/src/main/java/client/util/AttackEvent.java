package client.util;

import net.minecraft.world.entity.player.Player;

public class AttackEvent {
   private final Player playerEntity;
   private boolean flag;
   private boolean flag2;

   public AttackEvent(Player playerEntity2) {
      this.playerEntity = playerEntity2;
      this.flag = false;
      this.flag2 = false;
   }

   public boolean isFlag2() {
      return this.flag2;
   }

   public void setFlag2(boolean flag) {
      this.flag2 = flag;
   }

   public boolean isFlag() {
      return this.flag;
   }

   public Player getPlayerEntity() {
      return this.playerEntity;
   }

   public void setFlag(boolean flag2) {
      this.flag = flag2;
   }
}
