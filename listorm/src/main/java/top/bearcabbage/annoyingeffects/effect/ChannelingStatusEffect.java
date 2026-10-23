package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;

public class ChannelingStatusEffect extends AnnoyingStatusEffect {
  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return true;
  }

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
