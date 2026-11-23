package client.enums;

import client.data.EffectLevel;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public enum PotionType {
   SNOTVORNOE(
      TrackedItem.SNOTVORNOE,
      "Снотворка",
      getEffectLevelByRegistryEntryInt(MobEffects.WEAKNESS, 2),
      getEffectLevelByRegistryEntryInt(MobEffects.MINING_FATIGUE, 2),
      getEffectLevelByRegistryEntryInt(MobEffects.WITHER, 3),
      getEffectLevelByRegistryEntryInt(MobEffects.BLINDNESS, 1)
   ),
   PALADIN(
      TrackedItem.POTION_PALADINA,
      "Палладинка",
      getEffectLevelByRegistryEntryInt(MobEffects.HEALTH_BOOST, 3),
      getEffectLevelByRegistryEntryInt(MobEffects.RESISTANCE, 1),
      getEffectLevelByRegistryEntryInt(MobEffects.FIRE_RESISTANCE, 1),
      getEffectLevelByRegistryEntryInt(MobEffects.INVISIBILITY, 1)
   ),
   SVYATAYA_VODA(
      TrackedItem.SVYATAYA_VODA,
      "Святка",
      getEffectLevelByRegistryEntryInt(MobEffects.REGENERATION, 2),
      getEffectLevelByRegistryEntryInt(MobEffects.INVISIBILITY, 2)
   ),
   ASSASIN(
      TrackedItem.POTION_ASSASINA,
      "Ассасинка",
      getEffectLevelByRegistryEntryInt(MobEffects.STRENGTH, 4),
      getEffectLevelByRegistryEntryInt(MobEffects.SPEED, 3),
      getEffectLevelByRegistryEntryInt(MobEffects.HASTE, 1)
   ),
   RADIACIYA(
      TrackedItem.POTION_RADIACII,
      "Радиация",
      getEffectLevelByRegistryEntryInt(MobEffects.POISON, 2),
      getEffectLevelByRegistryEntryInt(MobEffects.WITHER, 2),
      getEffectLevelByRegistryEntryInt(MobEffects.SLOWNESS, 3),
      getEffectLevelByRegistryEntryInt(MobEffects.HUNGER, 5),
      getEffectLevelByRegistryEntryInt(MobEffects.GLOWING, 1)
   );

   public final TrackedItem trackedItem;
   public final String text;
   private final List<EffectLevel> list;
   private static final PotionType[] potionTypeArray = getPotionTypeArray();

   private PotionType(TrackedItem trackedItem2, String text2, EffectLevel... effectLevel) {
      this.trackedItem = trackedItem2;
      this.text = text2;
      this.list = List.of(effectLevel);
   }

   public boolean isStatusEffectInstance(MobEffectInstance statusEffectInstance) {
      if (statusEffectInstance == null) {
         return false;
      } else {
         for (EffectLevel effectlevel : this.list) {
            if (effectlevel.isStatusEffectInstance(statusEffectInstance)) {
               return true;
            }
         }

         return false;
      }
   }

   private static MobEffectInstance getStatusEffectInstanceByLivingEntityEffectLevel(LivingEntity livingEntity, EffectLevel effectLevel) {
      MobEffectInstance statuseffectinstance = livingEntity.getEffect(effectLevel.getType());
      return statuseffectinstance != null && effectLevel.isStatusEffectInstance(statuseffectinstance) ? statuseffectinstance : null;
   }

   private static PotionType[] getPotionTypeArray() {
      return new PotionType[]{SNOTVORNOE, PALADIN, SVYATAYA_VODA, ASSASIN, RADIACIYA};
   }

   public MobEffectInstance getStatusEffectInstanceByLivingEntity(LivingEntity livingEntity) {
      return livingEntity == null ? null : getStatusEffectInstanceByLivingEntityEffectLevel(livingEntity, this.list.getFirst());
   }

   public static PotionType getPotionTypeByString(String text) {
      return Enum.valueOf(PotionType.class, text);
   }

   public boolean isLivingEntity(LivingEntity livingEntity) {
      if (livingEntity == null) {
         return false;
      } else {
         for (EffectLevel effectlevel : this.list) {
            if (getStatusEffectInstanceByLivingEntityEffectLevel(livingEntity, effectlevel) == null) {
               return false;
            }
         }

         return true;
      }
   }

   private static EffectLevel getEffectLevelByRegistryEntryInt(Holder registryEntry, int count) {
      return new EffectLevel(registryEntry, count);
   }
}
