package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import top.bearcabbage.annoyingeffects.effecttags.NightMareStatusEffectTag;
import top.bearcabbage.annoyingeffects.effecttags.SubtleStatusEffectTag;

public class HorselessStatusEffect extends AnnoyingStatusEffect
    implements SubtleStatusEffectTag, NightMareStatusEffectTag {
  public HorselessStatusEffect() {
    super(); // 显示的颜色
  }

  // 这个方法在每个 tick 都会调用，以检查是否应应用药水效果
  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return true;
  }

  // 这个方法在应用药水效果时会被调用，所以我们可以在这里实现自定义功能。
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
