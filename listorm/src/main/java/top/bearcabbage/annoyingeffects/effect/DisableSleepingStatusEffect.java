package top.bearcabbage.annoyingeffects.effect;

import static net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Phantom;
import top.bearcabbage.annoyingeffects.effecttags.HandicapStatusEffectTag;
import top.bearcabbage.annoyingeffects.effecttags.NightMareStatusEffectTag;

public class DisableSleepingStatusEffect extends AnnoyingStatusEffect
    implements HandicapStatusEffectTag, NightMareStatusEffectTag {
  public static final int AVERAGE_SPAWN_INTERVAL = 300;
  public static final Double HEIGHT = 10.0;

  public DisableSleepingStatusEffect() {
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
    //        if(entity.getRandom().nextInt(20)==0 && entity.isSleeping() &&
    // !entity.getWorld().isClient){
    //            entity.wakeUp();
    //        }
    //        double random = Math.random();
    if (entity instanceof ServerPlayer serverPlayer
        && entity.getRandom().nextInt(AVERAGE_SPAWN_INTERVAL) <= amplifier
        && serverPlayer.level().canSeeSky(serverPlayer.blockPosition())) {
      Phantom phantom = new Phantom(EntityType.PHANTOM, serverPlayer.level());
      phantom.forceAddEffect(new MobEffectInstance(FIRE_RESISTANCE, 2400), null);
      phantom.setPos(serverPlayer.getX(), serverPlayer.getY() + HEIGHT, serverPlayer.getZ());
      serverPlayer.level().addFreshEntity(phantom);
    }
    return true;
  }
}
