package client.util;

import client.enums.VoiceIcon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class NameTagEntry {
   public Player playerEntity;
   public Vec3 vec3d;
   public String text;
   public String text2;
   public String text3;
   public float value;
   public float value2;
   public final ItemStack[] itemStackArray = new ItemStack[4];
   public boolean flag;
   public ItemStack itemStack;
   public ItemStack itemStack2;
   public double value3;
   public boolean flag2;
   public VoiceIcon voiceIcon = VoiceIcon.NONE;

   public void onVec3dFloatBooleanItemStackPlayerEntityItemStackStringDoubleFloatStringVoiceIconStringBoolean(
      Vec3 vec3d2,
      float value4,
      boolean flag3,
      ItemStack itemStack3,
      Player playerEntity2,
      ItemStack itemStack4,
      String text4,
      double value5,
      float value6,
      String text5,
      VoiceIcon voiceIcon2,
      String text6,
      boolean flag4
   ) {
      this.playerEntity = playerEntity2;
      this.vec3d = vec3d2;
      this.text = text4;
      this.text2 = text5;
      this.text3 = text6;
      this.value = value6;
      this.value2 = value4;
      this.flag = flag4;
      this.itemStack = itemStack4;
      this.itemStack2 = itemStack3;
      this.value3 = value5;
      this.flag2 = flag3;
      this.voiceIcon = voiceIcon2;
   }

   public void update() {
      this.playerEntity = null;
      this.vec3d = null;
      this.text = null;
      this.text2 = null;
      this.text3 = null;

      for (int i = 0; i < this.itemStackArray.length; i++) {
         this.itemStackArray[i] = null;
      }

      this.itemStack = null;
      this.itemStack2 = null;
   }
}
