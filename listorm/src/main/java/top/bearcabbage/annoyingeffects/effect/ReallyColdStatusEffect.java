package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.world.entity.LivingEntity;

public class ReallyColdStatusEffect extends TickingAnnoyingStatusEffect {
  @Override
  public boolean applyEffectTick(
      net.minecraft.server.level.ServerLevel serverLevel, LivingEntity entity, int amplifier) {
    entity.setTicksFrozen(entity.getTicksFrozen() + 7 * (1 + amplifier));
    return true;
  }
}
