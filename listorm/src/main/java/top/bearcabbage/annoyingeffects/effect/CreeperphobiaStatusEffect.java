// 并没有做完，没有办法正常工作。

package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import top.bearcabbage.annoyingeffects.effecttags.SubtleStatusEffectTag;

public class CreeperphobiaStatusEffect extends AnnoyingStatusEffect
    implements SubtleStatusEffectTag {
  public static final int AVERAGE_SOUND_INTERVAL = 1000;

  public CreeperphobiaStatusEffect() {
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
    RandomSource random = entity.getRandom();
    if (entity instanceof ServerPlayer serverPlayer
        && random.nextInt(AVERAGE_SOUND_INTERVAL) <= amplifier) {
      serverPlayer.connection.send(
          new ClientboundSoundPacket(
              BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.CREEPER_PRIMED),
              SoundSource.HOSTILE,
              serverPlayer.getX() + 3 * random.nextGaussian(),
              serverPlayer.getY() + 3 * random.nextGaussian(),
              serverPlayer.getZ() + random.nextGaussian(),
              1.0F,
              1.0F,
              0));
    }
    return true;
  }
}
