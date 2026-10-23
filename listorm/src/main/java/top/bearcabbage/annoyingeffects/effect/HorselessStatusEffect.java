package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class HorselessStatusEffect extends AnnoyingStatusEffect {
  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return true;
  }

  @Override
  public boolean applyEffectTick(
      net.minecraft.server.level.ServerLevel serverLevel, LivingEntity entity, int amplifier) {
    if (entity.level().isClientSide()) return true;
    ServerLevel world = (ServerLevel) entity.level();
    double distance = 4F * (1 + amplifier);
    for (Horse horse :
        world.getEntitiesOfClass(
            Horse.class,
            AABB.ofSize(entity.getEyePosition(), 2 * distance, 2 * distance, 2 * distance))) {
      if (horse.isAlive()
          && horse.position().distanceTo(entity.getEyePosition()) < distance
          && !horse.equals(entity)) {
        entity
            .level()
            .explode(
                horse,
                Explosion.getDefaultDamageSource(horse.level(), horse),
                null,
                horse.getX(),
                horse.getY(0.0625F),
                horse.getZ(),
                6.0F,
                true,
                Level.ExplosionInteraction.MOB);
        horse.kill(serverLevel);
      }
    }
    return true;
  }
}
