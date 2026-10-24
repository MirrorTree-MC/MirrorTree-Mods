package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.bearcabbage.annoyingeffects.utils.NoSavePlayerData;

public class CarrotCurseStatusEffect extends TickingAnnoyingStatusEffect {
  public static final NoSavePlayerData<Integer> CarrotTicks = new NoSavePlayerData<>(10000);

  @Override
  public boolean applyEffectTick(
      net.minecraft.server.level.ServerLevel serverLevel, LivingEntity entity, int amplifier) {
    if (!(entity instanceof Player player)) return false;
    if (entity.level().isClientSide()) return true;
    if (CarrotTicks.get(player) > 0) return false;
    player.addItem(new ItemStack(Items.POISONOUS_POTATO, 64));
    return true;
  }
}
