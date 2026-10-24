package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

public class CrawlerStatusEffect extends TickingAnnoyingStatusEffect {
  @Override
  public boolean applyEffectTick(
      net.minecraft.server.level.ServerLevel serverLevel, LivingEntity entity, int amplifier) {
    entity.setPose(Pose.SWIMMING);
    return true;
  }
}
