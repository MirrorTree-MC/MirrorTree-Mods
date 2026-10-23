package top.bearcabbage.lanterninstorm.safety;

import eu.pb4.trinkets.api.*;
import java.util.function.Consumer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.bearcabbage.lanterninstorm.content.StormContent;

public final class DefenseEquipment {
  public record Defense(ItemStack stack, Consumer<ItemStack> replace) {}

  private static final Defense EMPTY = new Defense(ItemStack.EMPTY, stack -> {});

  public static boolean isDefense(ItemStack stack) {
    return stack.is(StormContent.FLASHLIGHT) || stack.is(StormContent.TALISMAN);
  }

  public static Defense find(Player player) {
    var inventory = TrinketsApi.getAttachment(player).getInventory("offhand/glove");
    TrinketSlotAccess glove = null;
    if (inventory != null) {
      for (int i = 0; i < inventory.getContainerSize(); i++)
        if (isDefense(inventory.getItem(i))) glove = inventory.getSlotAccess(i);
      if (inventory.hasCosmeticItems())
        for (int i = 0; i < inventory.getContainerSize(); i++)
          if (isDefense(inventory.getCosmeticItem(i))) glove = inventory.getCosmeticSlotAccess(i);
    }
    if (glove != null) return new Defense(glove.get(), glove::set);
    if (isDefense(player.getMainHandItem()))
      return new Defense(
          player.getMainHandItem(), s -> player.setItemInHand(InteractionHand.MAIN_HAND, s));
    if (isDefense(player.getOffhandItem()))
      return new Defense(
          player.getOffhandItem(), s -> player.setItemInHand(InteractionHand.OFF_HAND, s));
    return EMPTY;
  }
}
