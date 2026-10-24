package top.bearcabbage.annoyingeffects;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public interface StatusEffectInstanceStackHolder {
  void pushStatusEffect(MobEffectInstance effect);

  MobEffectInstance popStatusEffect();

  void queueStatusEffectRemoval(Holder<MobEffect> effect);
}
