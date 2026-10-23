package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.AABB;
import top.bearcabbage.annoyingeffects.effecttags.SubtleStatusEffectTag;

public class EndermanHostileStatusEffect extends AnnoyingStatusEffect
    implements SubtleStatusEffectTag {
  public EndermanHostileStatusEffect() {
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
    if (entity.level().isClientSide() || entity.tickCount % 20 != 0) return true;
    ServerLevel world = (ServerLevel) entity.level();
    for (EnderMan enderman :
        world.getEntitiesOfClass(
            EnderMan.class, AABB.ofSize(entity.getEyePosition(), 2048F, 2048F, 2048F))) {
      if (!enderman.isAngry() && !enderman.equals(entity)) {
        //                enderman.setAngryAt(entity.getUUID());
        enderman.setTarget(entity);
      }
    }
    return true;
  }
}
