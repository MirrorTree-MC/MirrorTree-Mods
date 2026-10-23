package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.bearcabbage.annoyingeffects.effecttags.SevereHandicapStatusEffectTag;

public class SwappingHandsStatusEffect extends AnnoyingStatusEffect
    implements SevereHandicapStatusEffectTag {
  public SwappingHandsStatusEffect() {
    super(); // 显示的颜色
  }

  // 这个方法在每个 tick 都会调用，以检查是否应应用药水效果
  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return duration % (1 + (19 >> amplifier)) == 0;
  }

  // 这个方法在应用药水效果时会被调用，所以我们可以在这里实现自定义功能。
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
