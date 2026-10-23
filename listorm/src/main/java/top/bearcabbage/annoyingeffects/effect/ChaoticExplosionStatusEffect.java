package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

public class ChaoticExplosionStatusEffect extends AnnoyingStatusEffect {
  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return true;
  }

  @Override
  public boolean applyEffectTick(
      net.minecraft.server.level.ServerLevel serverLevel, LivingEntity entity, int amplifier) {
    if (entity.getRandom().nextInt(50) <= amplifier && !entity.level().isClientSide()) {
      entity
          .level()
          .explode(
              entity,
              Explosion.getDefaultDamageSource(entity.level(), entity),
              null,
              entity.getX(),
              entity.getY(0.0625F),
              entity.getZ(),
              (float) Math.sqrt(1 + amplifier),
              true,
              Level.ExplosionInteraction.MOB);
    }
    return true;
  }
}
