package top.bearcabbage.lanterninstorm.safety;

import eu.pb4.trinkets.api.*;
import java.util.function.Consumer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.bearcabbage.lanterninstorm.content.StormContent;

public final class DefenseEquipment {
  public record Defense(ItemStack stack, Consumer<ItemStack> replace) {}

  public static boolean isDefense(ItemStack stack) {
    return stack.is(StormContent.FLASHLIGHT) || stack.is(StormContent.TALISMAN);
  }

  public static Defense find(Player player) {
    var attachment = TrinketsApi.getAttachment(player);
    final TrinketSlotAccess[] glove = {null};
    attachment.forEach(
        (slot, stack) -> {
          if (slot.reference().slot().equals("offhand/glove") && isDefense(stack)) glove[0] = slot;
        });
    if (glove[0] != null) return new Defense(glove[0].get(), s -> glove[0].set(s));
    if (isDefense(player.getMainHandItem()))
      return new Defense(
          player.getMainHandItem(), s -> player.setItemInHand(InteractionHand.MAIN_HAND, s));
    if (isDefense(player.getOffhandItem()))
      return new Defense(
          player.getOffhandItem(), s -> player.setItemInHand(InteractionHand.OFF_HAND, s));
    return new Defense(ItemStack.EMPTY, s -> {});
  }
}
