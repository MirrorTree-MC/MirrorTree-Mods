package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.world.entity.LivingEntity;

public class SpinStatusEffect extends TickingAnnoyingStatusEffect {
  public static final double ANGLE_PER_TICK = -9F;

  @Override
  public boolean applyEffectTick(
      net.minecraft.server.level.ServerLevel serverLevel, LivingEntity entity, int amplifier) {
    if (entity instanceof net.minecraft.world.entity.player.Player) return true;
    float yaw = entity.yRotO;
    yaw += (float) (ANGLE_PER_TICK * (1 + amplifier));
    entity.setYRot(yaw);
    return true;
  }
}
