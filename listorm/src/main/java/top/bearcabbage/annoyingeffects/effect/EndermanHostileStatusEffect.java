package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.AABB;

public class EndermanHostileStatusEffect extends TickingAnnoyingStatusEffect {
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
