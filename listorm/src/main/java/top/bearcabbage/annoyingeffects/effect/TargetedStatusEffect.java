package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import top.bearcabbage.annoyingeffects.effecttags.NightMareStatusEffectTag;
import top.bearcabbage.annoyingeffects.effecttags.SubtleStatusEffectTag;

public class TargetedStatusEffect extends AnnoyingStatusEffect
    implements SubtleStatusEffectTag, NightMareStatusEffectTag {
  public static final double RADIUS = 16F;
  public static final double MAX_ACCELERATION = 0.6F;
  public static final double DAMP = 0.95F;

  public TargetedStatusEffect() {
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
    //        if(!(entity instanceof PlayerEntity player)) return false;
    Level world = entity.level();
    Vec3 eyePos = entity.getEyePosition();
    double radius = RADIUS * (1 + amplifier);
    for (Projectile projectile :
        world.getEntitiesOfClass(
            Projectile.class, AABB.ofSize(eyePos, radius * 2, radius * 2, radius * 2))) {
      Vec3 vec = eyePos.subtract(projectile.position());
      double distance = eyePos.distanceTo(projectile.position());
      if (distance >= radius) continue;
      Vec3 v = projectile.getDeltaMovement();
      projectile.setDeltaMovement(
          v.scale(DAMP).add(vec.normalize().scale(MAX_ACCELERATION * (1 - distance / radius))));
    }
    return true;
  }
}
