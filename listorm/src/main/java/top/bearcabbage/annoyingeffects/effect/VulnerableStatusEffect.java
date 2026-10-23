package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class VulnerableStatusEffect extends AnnoyingStatusEffect {
  public static final boolean CAN_KILL_MOBS = false;

  @Override
  public void onMobHurt(
      net.minecraft.server.level.ServerLevel level,
      LivingEntity entity,
      int amplifier,
      DamageSource source,
      float amount) {
    float health = entity.getHealth();
    if (CAN_KILL_MOBS && !(entity instanceof Player)) {
      entity.setHealth(health - amount * 0.5F * (1 + amplifier));
      return;
    }
    if (health <= 0.01F) return;
    entity.setHealth(Math.max(0.01F, health - amount * 0.5F * (1 + amplifier)));
  }
}
