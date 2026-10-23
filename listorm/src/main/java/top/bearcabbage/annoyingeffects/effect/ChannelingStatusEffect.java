package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import top.bearcabbage.annoyingeffects.effecttags.AdaptableStatusEffectTag;
import top.bearcabbage.annoyingeffects.effecttags.DamageStatusEffectTag;
import top.bearcabbage.annoyingeffects.effecttags.NightMareStatusEffectTag;
import top.bearcabbage.annoyingeffects.effecttags.TerrachangingStatusEffectTag;

public class ChannelingStatusEffect extends AnnoyingStatusEffect
    implements DamageStatusEffectTag,
        AdaptableStatusEffectTag,
        TerrachangingStatusEffectTag,
        NightMareStatusEffectTag {
  public ChannelingStatusEffect() {
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
    long random_seed = entity.getRandom().nextLong();
    RandomSource random = RandomSource.create(random_seed);
    if (!entity.level().isClientSide() && random.nextInt(20) <= amplifier) {
      ServerLevel world = (ServerLevel) entity.level();
      if (!world.isThundering()) return false;
      double x = entity.getX() + random.nextIntBetweenInclusive(-10, 10);
      double y = entity.getY();
      double z = entity.getZ() + random.nextIntBetweenInclusive(-10, 10);
      for (int i = -10; i < 10; ++i) {
        if (world.canSeeSky(net.minecraft.core.BlockPos.containing(x, y + i, z))) {
          LightningBolt lightning = new LightningBolt(EntityType.LIGHTNING_BOLT, world);
          lightning.setPos(x, y + i, z);
          world.addFreshEntity(lightning);
          break;
        }
      }
    }
    return true;
  }
}
