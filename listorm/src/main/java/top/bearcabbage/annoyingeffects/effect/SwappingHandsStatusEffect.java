package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class SwappingHandsStatusEffect extends AnnoyingStatusEffect {
  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return duration % (1 + (19 >> amplifier)) == 0;
  }

  @Override
  public boolean applyEffectTick(
      net.minecraft.server.level.ServerLevel serverLevel, LivingEntity entity, int amplifier) {
    ItemStack main = entity.getMainHandItem();
    ItemStack off = entity.getOffhandItem();
    entity.setItemInHand(InteractionHand.MAIN_HAND, off);
    entity.setItemInHand(InteractionHand.OFF_HAND, main);
    return true;
  }
}
