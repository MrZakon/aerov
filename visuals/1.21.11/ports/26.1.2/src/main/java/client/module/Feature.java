package client.module;

import client.api.UiMetrics;
import client.data.SystemFriend;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.Level;

public class Feature implements UiMetrics {
   public static long NANOS_PER_SECOND = 1000000000L;
   public static Minecraft mc = Minecraft.getInstance();

   public boolean isFriend(Player playerEntity) {
      return SystemFriend.getInstance().isPlayerEntity(playerEntity);
   }

   public Player player() {
      return mc.player;
   }

   protected InteractionHand offHand() {
      return InteractionHand.OFF_HAND;
   }

   public void sendMessage(String text2) {
      if (this.inGame()) {
         mc.player.sendSystemMessage(Component.nullToEmpty(text2));
      }
   }

   protected void clickSlot(int count) {
      MultiPlayerGameMode clientplayerinteractionmanager = mc.gameMode;
      LocalPlayer clientplayerentity = mc.player;
      if (clientplayerinteractionmanager != null && clientplayerentity != null) {
         clientplayerinteractionmanager.handleContainerInput(clientplayerentity.containerMenu.containerId, count, 0, ContainerInput.PICKUP, clientplayerentity);
      }
   }

   public boolean isFriend(LivingEntity livingEntity) {
      return SystemFriend.getInstance().isLivingEntity(livingEntity);
   }

   protected void quickMoveSlot(int count) {
      this.clickSlot(count < 9 ? count + 36 : count);
      this.clickSlot(6);
      this.clickSlot(count < 9 ? count + 36 : count);
   }

   public boolean isFriend(String text2) {
      return SystemFriend.getInstance().isString(text2);
   }

   protected void swapSlots(int count, int count2) {
      this.clickSlot(count < 9 ? count + 36 : count);
      this.clickSlot(count2 < 9 ? count2 + 36 : count2);
      this.clickSlot(count < 9 ? count + 36 : count);
   }

   public void sendPrefixedMessage(String text2) {
      if (this.inGame()) {
         mc.player.sendSystemMessage(Component.nullToEmpty(text2));
      }
   }

   protected ServerLevel serverWorld() {
      return mc.getSingleplayerServer() != null ? mc.getSingleplayerServer().getLevel(Objects.requireNonNull(mc.level).dimension()) : null;
   }

   protected void interactItem(InteractionHand hand) {
      MultiPlayerGameMode clientplayerinteractionmanager = mc.gameMode;
      LocalPlayer clientplayerentity = mc.player;
      if (clientplayerinteractionmanager != null && clientplayerentity != null) {
         clientplayerinteractionmanager.useItem(clientplayerentity, hand);
      }
   }

   protected InteractionHand mainHand() {
      return InteractionHand.MAIN_HAND;
   }

   public Level world() {
      return mc.level;
   }

   public Minecraft client() {
      return mc;
   }

   protected Screen currentScreen() {
      return mc.screen;
   }

   protected ClientPacketListener networkHandler() {
      return mc.getConnection();
   }

   protected Inventory inventory() {
      LocalPlayer clientplayerentity = mc.player;
      return clientplayerentity != null ? clientplayerentity.getInventory() : null;
   }

   public GameRenderer gameRenderer() {
      return mc.gameRenderer;
   }

   protected Options options() {
      return mc.options;
   }

   protected ClientLevel clientWorld() {
      return mc.level;
   }

   protected LocalPlayer clientPlayer() {
      return mc.player;
   }

   protected MultiPlayerGameMode interactionManager() {
      return mc.gameMode;
   }

   protected boolean notInGame() {
      return !this.inGame();
   }

   protected boolean inGame() {
      return mc.player != null && mc.level != null;
   }
}
