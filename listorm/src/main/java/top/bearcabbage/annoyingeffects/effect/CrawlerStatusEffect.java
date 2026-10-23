package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import top.bearcabbage.annoyingeffects.effecttags.AdaptableStatusEffectTag;
import top.bearcabbage.annoyingeffects.effecttags.NightMareStatusEffectTag;

public class CrawlerStatusEffect extends AnnoyingStatusEffect
    implements AdaptableStatusEffectTag, NightMareStatusEffectTag {
  public CrawlerStatusEffect() {
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
    entity.setPose(Pose.SWIMMING);
    return true;
  }
}
