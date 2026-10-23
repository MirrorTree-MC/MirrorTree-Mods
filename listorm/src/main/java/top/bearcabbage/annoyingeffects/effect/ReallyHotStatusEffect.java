package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.world.entity.LivingEntity;

public class ReallyHotStatusEffect extends AnnoyingStatusEffect {
  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return true;
  }

  @Override
  public boolean applyEffectTick(
      net.minecraft.server.level.ServerLevel serverLevel, LivingEntity entity, int amplifier) {
    if (entity.getRemainingFireTicks() <= 260 + amplifier * 200)
      entity.setRemainingFireTicks(entity.getRemainingFireTicks() + 40);
    return true;
  }
}
