package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.world.entity.LivingEntity;

public class ReallyColdStatusEffect extends AnnoyingStatusEffect {
  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return true;
  }

  @Override
  public boolean applyEffectTick(
      net.minecraft.server.level.ServerLevel serverLevel, LivingEntity entity, int amplifier) {
    entity.setTicksFrozen(entity.getTicksFrozen() + 7 * (1 + amplifier));
    return true;
  }
}
