package top.bearcabbage.lanterninstorm.safety;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;
import top.bearcabbage.annoyingeffects.effect.TanglingNightmareStatusEffect;
import top.bearcabbage.lanterninstorm.LanternInStormAPI;
import top.bearcabbage.lanterninstorm.content.StormContent;
import top.bearcabbage.lanterninstorm.network.StormNetworking;
import top.bearcabbage.lanterninstorm.progression.PlayerStore;

public final class SafetyService {
  private static final Identifier BEDROOM =
      Identifier.fromNamespaceAndPath("mirrortree", "bedroom");

  public static void tick(ServerPlayer player) {
    var state = PlayerStore.get(player);
    if (state.graceTicks > 0) state.graceTicks--;
    if (player.isCreative() || player.isSpectator() || ++state.tick % 20 != 0) return;
    boolean nearLamp = LanternIndex.isSafe(player.level(), player.position());
    boolean nearSpawn =
        state.spawn != null
            && state.spawn.length == 3
            && player.level().dimension().identifier().toString().equals(state.spawnDimension)
            && LanternIndex.within(
                player.position(),
                net.minecraft.world.phys.Vec3.atCenterOf(
                    new BlockPos(state.spawn[0], state.spawn[1], state.spawn[2])),
                16);
    boolean safe =
        player.level().dimension().identifier().equals(BEDROOM)
            || nearLamp
            || nearSpawn
            || state.graceTicks > 0
            || LanternInStormAPI.safeWorlds.contains(player.level().dimension());
    var defense = DefenseEquipment.find(player);
    ItemStack stack = defense.stack();
    if (safe) {
      if (!state.safe || !state.cleared
          || player.hasEffect(AnnoyingEffects.TANGLING_DREAMS)
          || player.hasEffect(AnnoyingEffects.TANGLING_NIGHTMARE))
        clear(player, ItemStack.EMPTY, false);
      StormNetworking.bar(player, false, 0);
      TanglingNightmareStatusEffect.AnnoyingBar.reset(player);
      state.cleared = true;
      if (nearLamp && stack.is(StormContent.FLASHLIGHT))
        stack.setDamageValue(Math.max(0, stack.getDamageValue() - 20));
    } else {
      boolean vanilla = player.level().dimension().identifier().getNamespace().equals("minecraft");
      StormNetworking.bar(player, true, -1);
      if (stack.isEmpty()) {
        player.addEffect(
            new MobEffectInstance(
                vanilla ? AnnoyingEffects.TANGLING_DREAMS : AnnoyingEffects.TANGLING_NIGHTMARE,
                200));
        state.cleared = false;
      } else {
        boolean flashlight = stack.is(StormContent.FLASHLIGHT);
        boolean exhausted = stack.getDamageValue() >= stack.getMaxDamage() - 1;
        // A depleted doll still occupies its slot, but must not cleanse nightmare effects.
        // Keep the vanilla-dimension exemption: dolls there work without consuming charge.
        if (!state.cleared && (!flashlight || vanilla || !exhausted))
          state.cleared = clear(player, stack, vanilla && flashlight);
        if (!flashlight || (!vanilla && !exhausted))
          stack.hurtAndBreak(1, player.level(), player, i -> {});
        if (!vanilla && (!flashlight || exhausted)) {
          player.addEffect(new MobEffectInstance(AnnoyingEffects.TANGLING_DREAMS, 200));
          state.cleared = false;
        }
        if (!flashlight && stack.isEmpty()) {
          for (int i = player.getInventory().getContainerSize() - 1; i >= 0; i--) {
            ItemStack next = player.getInventory().getItem(i);
            if (next.is(StormContent.TALISMAN)) {
              defense.replace().accept(next.split(1));
              break;
            }
          }
        }
      }
    }
    state.safe = safe;
  }

  private static boolean clear(ServerPlayer player, ItemStack stack, boolean free) {
    boolean enough = true;
    for (var effect : new ArrayList<>(player.getActiveEffects())) {
      if (effect.getEffect().value().isBeneficial()) continue;
      if (!stack.isEmpty() && !free) {
        if (stack.getDamageValue() < stack.getMaxDamage() - 3)
          stack.hurtAndBreak(2, player.level(), player, i -> {});
        else enough = false;
      }
      player.removeEffect(effect.getEffect());
    }
    return enough;
  }
}
