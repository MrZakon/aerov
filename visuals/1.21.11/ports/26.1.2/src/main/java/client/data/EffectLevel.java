package client.data;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public record EffectLevel(Holder<MobEffect> type, int level) {
   public boolean isStatusEffectInstance(MobEffectInstance statusEffectInstance) {
      return statusEffectInstance.getEffect().value() == this.type.value() && statusEffectInstance.getAmplifier() == this.level - 1;
   }

   public Holder<MobEffect> getType() {
      return this.type;
   }

   public int getLevel() {
      return this.level;
   }
}
