package top.bearcabbage.lanterninstorm.progression;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.puffish.skillsmod.api.SkillsAPI;
import top.bearcabbage.annoyingeffects.effect.CarrotCurseStatusEffect;

public final class ProgressionService {
  public static int experience(String type, int count, int amount) {
    return switch (type) {
      case "food" -> 10 / Math.max(1, count) + 1;
      case "biome" -> count == 1 ? 30 : 0;
      case "advancement" -> amount * 10;
      case "ore" -> Math.max(11 - count, 1);
      default -> 0;
    };
  }

  public static void award(ServerPlayer player, String type, String key, int amount) {
    var state = PlayerStore.get(player);
    if ((type.equals("biome") || type.equals("advancement")) && state.count(type, key) > 0) return;
    int count = state.increment(type, key), xp = experience(type, count, amount);
    if (xp > 0)
      SkillsAPI.updateExperienceSources(
          player, SpiritExperienceSource.class, source -> source.type().equals(type) ? xp : 0);
  }

  public static void food(ServerPlayer player, ItemStack stack) {
    award(player, "food", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), 1);
    int carrot = CarrotCurseStatusEffect.CarrotTicks.get(player);
    CarrotCurseStatusEffect.CarrotTicks.set(
        player, Math.max(0, carrot + (stack.is(Items.CARROT) ? 10000 : -1000)));
  }

  public static void tick(ServerPlayer player) {
    if (player.tickCount % 20 != 0) return;
    if (!player
        .level()
        .dimension()
        .identifier()
        .equals(Identifier.fromNamespaceAndPath("mirrortree", "bedroom")))
      player
          .level()
          .getBiome(player.blockPosition())
          .unwrapKey()
          .ifPresent(key -> award(player, "biome", key.identifier().toString(), 1));
    if (player.tickCount % 1200 == 0) PlayerStore.save(player);
  }
}
