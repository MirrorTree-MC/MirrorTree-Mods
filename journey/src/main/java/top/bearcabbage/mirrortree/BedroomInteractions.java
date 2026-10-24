package top.bearcabbage.mirrortree;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

/** Right-click exceptions to the ordinary-player protection in the bedroom. */
public final class BedroomInteractions {
 private static final Identifier SURVIVAL_SHOP =
   Identifier.fromNamespaceAndPath("numismatic-overhaul", "shop");
 private BedroomInteractions() {}

 public static boolean allowsBlock(Block block) {
  return allowsBlockId(BuiltInRegistries.BLOCK.getKey(block));
 }

 public static boolean allowsBlockId(Identifier id) {
  String namespace = id.getNamespace();
  String path = id.getPath();
  if (namespace.equals("kaleidoscope_doll") && path.contains("doll")) return true;
  if (namespace.equals("selling-bin") && path.equals("wooden_bin")) return true;
  if (namespace.equals("universal_shops")
    && (path.equals("trade_block") || path.equals("admin_trade_block"))) return true;
  return path.equals("shop") || path.startsWith("shop_")
    || path.endsWith("_shop") || path.contains("_shop_");
 }

 public static boolean allowsEntity(EntityType<?> type) {
  return allowsEntityId(BuiltInRegistries.ENTITY_TYPE.getKey(type));
 }

 public static boolean allowsEntityId(Identifier id) {
  return id.equals(Identifier.fromNamespaceAndPath("kaleidoscope_doll", "doll"));
 }

 public static boolean isSurvivalShop(Block block) {
  return BuiltInRegistries.BLOCK.getKey(block).equals(SURVIVAL_SHOP);
 }

 public static boolean isSurvivalShopItem(ItemStack stack) {
  return stack.getItem() instanceof BlockItem item && isSurvivalShop(item.getBlock());
 }
}
