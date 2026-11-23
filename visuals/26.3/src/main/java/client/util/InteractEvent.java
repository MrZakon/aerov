package client.util;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;

public class InteractEvent {
   private final Player playerEntity;
   private final InteractionHand hand;
   private final HitResult hitResult;
   private boolean flag;

   public InteractEvent(Player playerEntity2, InteractionHand hand2, HitResult hitResult2) {
      this.playerEntity = playerEntity2;
      this.hand = hand2;
      this.hitResult = hitResult2;
      this.flag = false;
   }

   public HitResult getHitResult() {
      return this.hitResult;
   }

   public boolean isFlag() {
      return this.flag;
   }

   public void setFlag(boolean flag2) {
      this.flag = flag2;
   }

   public InteractionHand getHand() {
      return this.hand;
   }

   public Player getPlayerEntity() {
      return this.playerEntity;
   }
}
