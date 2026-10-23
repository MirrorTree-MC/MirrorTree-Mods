package top.bearcabbage.lanterninstorm.effects;

import java.util.*;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;
import top.bearcabbage.annoyingeffects.StatusEffectInstanceStackHolder;
import top.bearcabbage.annoyingeffects.effect.*;

public final class EffectRuntime {
  public static void initialize() {
    UseItemCallback.EVENT.register(
        (player, level, hand) -> {
          ItemStack stack = player.getItemInHand(hand);
          if (player.isSpectator()) return InteractionResult.PASS;
          if (stack.has(DataComponents.FOOD)
              && player.hasEffect(AnnoyingEffects.ANOREXIA)
              && !(stack.is(Items.CARROT)
                  && stack.getHoverName().getString().equals("AC is watching you"))) {
            if (!level.isClientSide()) {
              ((StatusEffectInstanceStackHolder) player)
                  .pushStatusEffect(new MobEffectInstance(MobEffects.NAUSEA, 300));
              player.sendOverlayMessage(
                  Component.translatable("messages.annoyingeffects.anorexia"));
            }
            return InteractionResult.FAIL;
          }
          if (stack.is(Items.MILK_BUCKET) && player.hasEffect(AnnoyingEffects.TANGLING_NIGHTMARE)) {
            if (!level.isClientSide()) {
              ItemStack pumpkin = new ItemStack(Items.CARVED_PUMPKIN);
              pumpkin.enchant(
                  level
                      .registryAccess()
                      .lookupOrThrow(Registries.ENCHANTMENT)
                      .getOrThrow(Enchantments.BINDING_CURSE),
                  1);
              pumpkin.set(
                  DataComponents.ITEM_NAME,
                  Component.translatable("effect.annoyingeffects.tangling_nightmare"));
              player.setItemInHand(hand, pumpkin);
              player.sendOverlayMessage(
                  Component.translatable("messages.annoyingeffects.tangling_nightmare_milk"));
            }
            return InteractionResult.FAIL;
          }
          return InteractionResult.PASS;
        });
    UseBlockCallback.EVENT.register(
        (player, level, hand, hit) -> {
          if (player.isSpectator()) return InteractionResult.PASS;
          var block = level.getBlockState(hit.getBlockPos()).getBlock();
          if (player.hasEffect(AnnoyingEffects.DISABLE_CRAFTING_TABLE)
              && (block == Blocks.CRAFTING_TABLE || block == Blocks.CRAFTER)) {
            player.sendOverlayMessage(
                Component.translatable("messages.annoyingeffects.crafting_fatigue"));
            return InteractionResult.FAIL;
          }
          if (player.hasEffect(AnnoyingEffects.DISABLE_SLEEPING) && block instanceof BedBlock) {
            player.sendOverlayMessage(Component.translatable("messages.annoyingeffects.insomnia"));
            return InteractionResult.FAIL;
          }
          return InteractionResult.PASS;
        });
  }

  public static void tick(ServerPlayer player) {
    CarrotCurseStatusEffect.CarrotTicks.set(
        player, Math.max(0, CarrotCurseStatusEffect.CarrotTicks.get(player) - 1));
    int water =
        Math.max(
            0,
            WaterFillingStatusEffect.WaterTicks.get(player) + (player.isInWaterOrRain() ? 1 : -1));
    WaterFillingStatusEffect.WaterTicks.set(player, player.isAlive() ? water : 0);
    if (player.tickCount % 20 == 0
        && player.hasEffect(AnnoyingEffects.TANGLING_NIGHTMARE)
        && water > 1000) {
      String key =
          water <= 1500
              ? "warning"
              : player.hasEffect(AnnoyingEffects.WATER_FILLING)
                  ? "water"
                  : player.isInWaterOrRain() ? "danger" : "safe";
      player.sendOverlayMessage(
          Component.translatable("messages.annoyingeffects.water_filling." + key));
    }
  }

  public static void clear() {
    CarrotCurseStatusEffect.CarrotTicks.clear();
    WaterFillingStatusEffect.WaterTicks.clear();
    TanglingNightmareStatusEffect.AnnoyingBar.clear();
  }

  public static void leave(ServerPlayer player) {
    CarrotCurseStatusEffect.CarrotTicks.reset(player);
    WaterFillingStatusEffect.WaterTicks.reset(player);
    TanglingNightmareStatusEffect.AnnoyingBar.reset(player);
  }

  public static void vanish(ServerPlayer player) {
    var effect = player.getEffect(AnnoyingEffects.CURSE_OF_VANISHING);
    if (effect == null) return;
    var enchantment =
        player
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.VANISHING_CURSE);
    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
      var item = player.getInventory().getItem(i);
      if (!item.isEmpty() && player.getRandom().nextInt(5) <= effect.getAmplifier())
        item.enchant(enchantment, 1);
    }
  }
}
