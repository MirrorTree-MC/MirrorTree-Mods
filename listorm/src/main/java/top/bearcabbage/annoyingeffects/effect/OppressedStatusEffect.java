package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.world.entity.LivingEntity;

public class OppressedStatusEffect extends TickingAnnoyingStatusEffect {
  public static final float PITCH = 18.0F;

  @Override
  public boolean applyEffectTick(
      net.minecraft.server.level.ServerLevel serverLevel, LivingEntity entity, int amplifier) {
    if (entity instanceof net.minecraft.world.entity.player.Player) return true;
    float pitch = entity.getXRot();
    float min_pitch = Math.min(PITCH * (1 + amplifier), 90F);
    entity.setXRot(Math.max(pitch, min_pitch));
    return true;
  }
}
