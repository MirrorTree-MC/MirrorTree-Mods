package top.bearcabbage.lanterninstorm.content;

import eu.pb4.trinkets.api.*;
import eu.pb4.trinkets.api.callback.TrinketCallback;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.*;

public final class StormContent {
  public static final String ID = "lantern-in-storm";
  public static final Map<String, SpiritLanternBlock> LAMPS = new LinkedHashMap<>();
  public static final List<Item> ITEMS = new ArrayList<>();
  public static Item TALISMAN;
  public static Item FLASHLIGHT;
  public static Item LANTERN_CORE;

  public static Identifier id(String path) {
    return Identifier.fromNamespaceAndPath(ID, path);
  }

  public static void initialize() {
    registerLamp("cubic_glass_lantern", 0, SoundType.GLASS, 10, MapColor.COLOR_CYAN);
    registerLamp("white_paper_lantern", 0, SoundType.LANTERN, 10, MapColor.SNOW);
    registerLamp("oak_wooden_lantern", 0, SoundType.WOOD, 10, MapColor.COLOR_BROWN);
    registerLamp("spruce_wooden_lantern", 0, SoundType.WOOD, 10, MapColor.COLOR_BROWN);
    registerLamp("birch_wooden_lantern", 0, SoundType.WOOD, 10, MapColor.COLOR_BROWN);
    registerLamp("jungle_wooden_lantern", 0, SoundType.WOOD, 10, MapColor.COLOR_BROWN);
    registerLamp("acacia_wooden_lantern", 0, SoundType.WOOD, 10, MapColor.COLOR_BROWN);
    registerLamp("dark_oak_wooden_lantern", 0, SoundType.WOOD, 10, MapColor.COLOR_BROWN);
    registerLamp("mangrove_wooden_lantern", 0, SoundType.WOOD, 10, MapColor.COLOR_BROWN);
    registerLamp("cherry_wooden_lantern", 0, SoundType.WOOD, 10, MapColor.COLOR_BROWN);
    registerLamp("bamboo_wooden_lantern", 0, SoundType.WOOD, 10, MapColor.COLOR_BROWN);
    registerLamp("crimson_wooden_lantern", 0, SoundType.WOOD, 10, MapColor.COLOR_BROWN);
    registerLamp("warped_wooden_lantern", 0, SoundType.WOOD, 10, MapColor.COLOR_BROWN);
    registerLamp("fir_wooden_lantern", 0, SoundType.STONE, 10, MapColor.COLOR_BROWN);
    registerLamp("pine_wooden_lantern", 0, SoundType.STONE, 10, MapColor.COLOR_BROWN);
    registerLamp("maple_wooden_lantern", 0, SoundType.STONE, 10, MapColor.COLOR_BROWN);
    registerLamp("redwood_wooden_lantern", 0, SoundType.STONE, 10, MapColor.COLOR_BROWN);
    registerLamp("mahogany_wooden_lantern", 0, SoundType.STONE, 10, MapColor.COLOR_BROWN);
    registerLamp("jacaranda_wooden_lantern", 0, SoundType.STONE, 10, MapColor.COLOR_BROWN);
    registerLamp("palm_wooden_lantern", 0, SoundType.STONE, 10, MapColor.COLOR_BROWN);
    registerLamp("willow_wooden_lantern", 0, SoundType.STONE, 10, MapColor.COLOR_BROWN);
    registerLamp("dead_wooden_lantern", 0, SoundType.STONE, 10, MapColor.COLOR_BROWN);
    registerLamp("magic_wooden_lantern", 0, SoundType.STONE, 10, MapColor.COLOR_BROWN);
    registerLamp("umbran_wooden_lantern", 0, SoundType.STONE, 10, MapColor.COLOR_BROWN);
    registerLamp("hellbark_wooden_lantern", 0, SoundType.STONE, 0, MapColor.COLOR_BROWN);
    registerLamp("empyreal_wooden_lantern", 0, SoundType.STONE, 0, MapColor.COLOR_BROWN);
    registerLamp("midas_gold_lantern", 2, SoundType.METAL, 10, MapColor.GOLD);
    registerLamp("morkite_lantern", 4, SoundType.METAL, 10, MapColor.COLOR_GRAY);
    registerLamp("starrite_lantern", 8, SoundType.METAL, 10, MapColor.COLOR_LIGHT_BLUE);
    registerLamp("prometheum_lantern", 16, SoundType.METAL, 10, MapColor.COLOR_RED);
    registerLamp("snowman_lantern", 8, SoundType.GLASS, 10, MapColor.COLOR_CYAN);
    registerLamp("snake_newyear_lantern", 8, SoundType.GLASS, 10, MapColor.COLOR_CYAN);
    registerLamp("kongming_lantern_2025", 8, SoundType.GLASS, 10, MapColor.COLOR_CYAN);
    registerLamp("deathbear_lantern", 8, SoundType.WOOD, 10, MapColor.COLOR_CYAN);
    TALISMAN = registerItem("talisman", new Item.Properties().durability(120));
    FLASHLIGHT = registerItem("flashlight", new Item.Properties().durability(600));
    LANTERN_CORE = registerItem("lantern_core", new Item.Properties());
    TrinketCallback keep =
        new TrinketCallback() {
          @Override
          public TrinketDropRule getDropRule(
              ItemStack stack,
              TrinketSlotAccess slot,
              net.minecraft.world.entity.LivingEntity entity) {
            return TrinketDropRule.KEEP;
          }
        };
    TrinketCallback.setCallback(TALISMAN, keep);
    TrinketCallback.setCallback(FLASHLIGHT, keep);
    Registry.register(
        BuiltInRegistries.CREATIVE_MODE_TAB,
        id("content"),
        net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.lantern-in-storm"))
            .icon(() -> new ItemStack(LANTERN_CORE))
            .displayItems((parameters, output) -> ITEMS.forEach(output::accept))
            .build());
  }

  private static void registerLamp(
      String name, int radius, SoundType sound, int light, MapColor colour) {
    ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
    SpiritLanternBlock block =
        Registry.register(
            BuiltInRegistries.BLOCK,
            key,
            new SpiritLanternBlock(
                BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(colour)
                    .strength(10, 3600000)
                    .sound(sound)
                    .lightLevel(s -> light)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK),
                radius));
    LAMPS.put(name, block);
    if (!name.equals("white_paper_lantern")) {
      ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id(name));
      ITEMS.add(
          Registry.register(
              BuiltInRegistries.ITEM,
              itemKey,
              new BlockItem(
                  block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())));
    }
  }

  private static Item registerItem(String name, Item.Properties properties) {
    ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
    Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
    ITEMS.add(item);
    return item;
  }
}
