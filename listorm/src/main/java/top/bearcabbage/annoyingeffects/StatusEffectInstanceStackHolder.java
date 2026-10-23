package top.bearcabbage.annoyingeffects;

import net.minecraft.world.effect.MobEffectInstance;

public interface StatusEffectInstanceStackHolder {
  void pushStatusEffect(MobEffectInstance effect);

  MobEffectInstance popStatusEffect();
}
