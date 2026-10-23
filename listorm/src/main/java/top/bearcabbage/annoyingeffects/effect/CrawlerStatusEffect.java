package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

public class CrawlerStatusEffect extends AnnoyingStatusEffect {
  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return true;
  }

  @Override
  public boolean applyEffectTick(
      net.minecraft.server.level.ServerLevel serverLevel, LivingEntity entity, int amplifier) {
    entity.setPose(Pose.SWIMMING);
    return true;
  }
}
