package top.bearcabbage.lanterninstorm.test;

import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;
import top.bearcabbage.lanterninstorm.*;
import top.bearcabbage.lanterninstorm.content.*;
import top.bearcabbage.lanterninstorm.progression.*;
import top.bearcabbage.lanterninstorm.safety.*;

public final class StormGameTest implements FabricClientGameTest {
  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  @Override
  public void runTest(ClientGameTestContext context) {
    check(ProgressionService.experience("food", 1, 1) == 11, "first food XP");
    check(ProgressionService.experience("food", 10, 1) == 2, "tenth food XP");
    check(ProgressionService.experience("food", 11, 1) == 1, "repeat food XP floor");
    check(
        ProgressionService.experience("ore", 1, 1) == 10
            && ProgressionService.experience("ore", 50, 1) == 1,
        "ore XP decay");
    check(
        ProgressionService.experience("biome", 1, 1) == 30
            && ProgressionService.experience("biome", 2, 1) == 0,
        "biome XP once");
    check(ProgressionService.experience("advancement", 1, 5) == 50, "challenge XP");
    TestWorldSave save;
    try (var world = context.worldBuilder().create()) {
      save = world.getWorldSave();
      world
          .getServer()
          .runOnServer(
              server -> {
                LanternInStormAPI.addSafeWorld(Level.OVERWORLD);
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                check(StormContent.LAMPS.size() == 34, "all 34 lamps registered");
                var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
                for (String recipe :
                    List.of(
                        "talisman",
                        "lantern_core",
                        "flashlight",
                        "oak_wooden_lantern",
                        "fir_wooden_lantern",
                        "midas_gold_lantern")) {
                  boolean present =
                      server
                          .getRecipeManager()
                          .byKey(
                              net.minecraft.resources.ResourceKey.create(
                                  net.minecraft.core.registries.Registries.RECIPE,
                                  StormContent.id(recipe)))
                          .isPresent();
                  boolean expected =
                      recipe.equals("fir_wooden_lantern")
                          ? loader.isModLoaded("biomesoplenty")
                          : recipe.equals("midas_gold_lantern")
                              ? loader.isModLoaded("mythicmetals")
                              : true;
                  check(present == expected, "recipe loading conditions: " + recipe);
                }
                BlockPos pos = player.blockPosition().offset(3, 0, 0);
                var lamp = StormContent.LAMPS.get("oak_wooden_lantern");
                level.setBlockAndUpdate(
                    pos, lamp.defaultBlockState().setValue(SpiritLanternBlock.STARTUP, true));
                check(
                    LanternIndex.isSafe(level, Vec3.atCenterOf(pos).add(8, 8, 8)),
                    "inclusive cubic safe boundary");
                check(
                    !LanternIndex.isSafe(level, Vec3.atCenterOf(pos).add(8.01, 0, 0)),
                    "outside safe boundary");
                level.setBlockAndUpdate(pos, lamp.defaultBlockState());
                check(!LanternIndex.isSafe(level, Vec3.atCenterOf(pos)), "unlit lamp unsafe");
                LanternIndex.unload(level, level.getChunkAt(pos));
                LanternIndex.load(level, level.getChunkAt(pos));
                level.setBlockAndUpdate(
                    pos, lamp.defaultBlockState().setValue(SpiritLanternBlock.STARTUP, true));
                check(
                    LanternIndex.isSafe(level, Vec3.atCenterOf(pos)),
                    "chunk reload restores index");
                var experience =
                    net.puffish.skillsmod.api.SkillsAPI.getCategory(
                            Identifier.fromNamespaceAndPath("storm_test", "integration"))
                        .orElseThrow()
                        .getExperience()
                        .orElseThrow();
                int before = experience.getTotal(player);
                var state = PlayerStore.get(player);
                ProgressionService.award(player, "food", "test:food", 1);
                ProgressionService.award(player, "food", "test:food", 1);
                check(state.count("food", "test:food") == 2, "repeat food counter");
                ProgressionService.award(player, "biome", "test:biome", 1);
                ProgressionService.award(player, "biome", "test:biome", 1);
                check(state.count("biome", "test:biome") == 1, "biome deduplication");
                check(
                    experience.getTotal(player) - before == 47,
                    "food and biome XP reach actual Pufferfish category exactly once");
                new ItemStack(Items.APPLE).finishUsingItem(level, player);
                check(
                    state.count("food", "minecraft:apple") == 1,
                    "vanilla consumption event awards once");
                if (net.fabricmc.loader.api.FabricLoader.getInstance()
                    .isModLoaded("farmersdelight")) {
                  try {
                    var pie =
                        BuiltInRegistries.BLOCK.getValue(
                            Identifier.parse("farmersdelight:apple_pie"));
                    var piePos = pos.offset(0, 0, 5);
                    level.setBlockAndUpdate(
                        piePos.below(),
                        net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(piePos, pie.defaultBlockState());
                    player.getFoodData().setFoodLevel(10);
                    var consume =
                        pie.getClass()
                            .getDeclaredMethod(
                                "consumeBite",
                                Level.class,
                                BlockPos.class,
                                net.minecraft.world.level.block.state.BlockState.class,
                                net.minecraft.world.entity.player.Player.class);
                    consume.setAccessible(true);
                    consume.invoke(pie, level, piePos, pie.defaultBlockState(), player);
                    check(
                        state.count("food", "farmersdelight:apple_pie_slice") == 1,
                        "Farmer's Delight pie awards exactly once through food component");
                  } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Farmer's Delight fixture failed", e);
                  }
                }
                for (String advancementType : List.of("goal", "challenge")) {
                  var entry =
                      server
                          .getAdvancements()
                          .get(Identifier.parse("minecraft:storm_test_" + advancementType));
                  check(entry != null, "test advancement loaded");
                  int prior = experience.getTotal(player);
                  player.getAdvancements().award(entry, "done");
                  player.getAdvancements().award(entry, "done");
                  check(
                      experience.getTotal(player) - prior
                          == (advancementType.equals("goal") ? 10 : 50),
                      "advancement event awards exactly once");
                }
                var orePos = pos.offset(0, 0, -2);
                level.setBlockAndUpdate(
                    orePos, net.minecraft.world.level.block.Blocks.COAL_ORE.defaultBlockState());
                check(player.gameMode.destroyBlock(orePos), "ore broken through game mode");
                check(state.count("ore", "minecraft:coal_ore") == 1, "ore break event tracked");
                verifyDefenseSelection(player);
                var attachment = eu.pb4.trinkets.api.TrinketsApi.getAttachment(player);
                var glove = attachment.getSlotAccess("offhand/glove", 0);
                check(glove.isValid(), "offhand glove slot available");
                var flashlight = new ItemStack(StormContent.FLASHLIGHT);
                flashlight.setDamageValue(100);
                check(glove.set(flashlight), "flashlight equips in glove slot");
                check(
                    DefenseEquipment.find(player).stack() == flashlight,
                    "equipped flashlight selected");
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.teleportTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                state.tick = 19;
                state.graceTicks = 0;
                SafetyService.tick(player);
                check(flashlight.getDamageValue() == 80, "safe lamp charges flashlight by twenty");
                level.setBlockAndUpdate(pos, lamp.defaultBlockState());
                LanternInStormAPI.safeWorlds.clear();
                player.addEffect(
                    new MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 200));
                state.tick = 19;
                state.cleared = false;
                SafetyService.tick(player);
                check(
                    flashlight.getDamageValue() == 80,
                    "vanilla dimension flashlight costs no charge");
                check(
                    !player.hasEffect(net.minecraft.world.effect.MobEffects.POISON),
                    "flashlight removes harmful effect");
                glove.set(ItemStack.EMPTY);
                state.tick = 19;
                SafetyService.tick(player);
                check(
                    player.hasEffect(AnnoyingEffects.TANGLING_DREAMS),
                    "unsafe and unequipped triggers dreams");
                player.removeAllEffects();
                var nightmareKey =
                    net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        Identifier.parse("storm_test:nightmare"));
                var nightmare = server.getLevel(nightmareKey);
                check(nightmare != null, "custom nightmare dimension loads");
                player.teleportTo(nightmare, 0.5, 5, 0.5, Set.of(), 0, 0, false);
                state.tick = 19;
                state.graceTicks = 0;
                state.cleared = true;
                SafetyService.tick(player);
                check(
                    player.hasEffect(AnnoyingEffects.TANGLING_NIGHTMARE),
                    "custom dimension unequipped triggers strong nightmare");
                player.removeAllEffects();
                glove.set(flashlight);
                state.tick = 19;
                state.cleared = true;
                SafetyService.tick(player);
                check(
                    flashlight.getDamageValue() == 81,
                    "custom dimension flashlight consumes charge");
                flashlight.setDamageValue(flashlight.getMaxDamage() - 1);
                state.tick = 19;
                SafetyService.tick(player);
                check(!flashlight.isEmpty(), "exhausted flashlight retained for recharge");
                check(
                    player.hasEffect(AnnoyingEffects.TANGLING_DREAMS),
                    "exhausted flashlight permits weak nightmare");
                var dreams = player.getEffect(AnnoyingEffects.TANGLING_DREAMS);
                player.addEffect(
                    new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 200));
                state.cleared = false;
                for (int second = 0; second < 5; second++) {
                  state.tick = 19;
                  SafetyService.tick(player);
                  check(
                      player.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS),
                      "exhausted doll does not erase nightmare debuffs on subsequent seconds");
                  check(
                      player.getEffect(AnnoyingEffects.TANGLING_DREAMS) == dreams,
                      "exhausted doll refreshes dreams without removing the existing instance");
                }
                flashlight.setDamageValue(100);
                state.tick = 19;
                SafetyService.tick(player);
                check(
                    !player.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS),
                    "recharged doll regains cleansing");
                // Even an exhausted doll remains effective in vanilla dimensions.
                flashlight.setDamageValue(flashlight.getMaxDamage() - 1);
                player.teleportTo(
                    level,
                    pos.getX() + 32.5,
                    pos.getY() + .5,
                    pos.getZ() + .5,
                    Set.of(),
                    0,
                    0,
                    false);
                state.cleared = false;
                state.tick = 19;
                player.addEffect(
                    new MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 200));
                SafetyService.tick(player);
                check(
                    !player.hasEffect(net.minecraft.world.effect.MobEffects.POISON),
                    "vanilla exhausted doll keeps free cleansing");
                verifyBedroomSafety(player);
                verifyLampPlacement(player);
                player.removeAllEffects();
                glove.set(ItemStack.EMPTY);
                player.teleportTo(
                    level,
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    Set.of(),
                    0,
                    0,
                    false);
                LanternInStormAPI.addSafeWorld(Level.OVERWORLD);
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                level.setBlockAndUpdate(
                    pos, lamp.defaultBlockState().setValue(SpiritLanternBlock.STARTUP, true));
                LanternInStormAPI.setRTPSpawn(player, pos, false);
                PlayerStore.save(player);
                check(state.spawn[0] == pos.getX(), "origin saved");
                var respawn =
                    player.findRespawnPositionAndUseSpawnBlock(
                        false, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING);
                check(
                    respawn.position().equals(Vec3.atCenterOf(pos)),
                    "missing bed falls back to original lantern spawn");
                player.getInventory().add(new ItemStack(StormContent.FLASHLIGHT));
                // Every effect callback executes against a real server entity and world.
                var pig = EntityType.PIG.create(level, EntitySpawnReason.COMMAND);
                check(pig != null, "test entity created");
                pig.setPos(player.position().add(100, 4, 100));
                level.addFreshEntity(pig);
                int effects = 0;
                for (var entry : BuiltInRegistries.MOB_EFFECT.entrySet())
                  if (entry.getKey().identifier().getNamespace().equals("annoyingeffects")) {
                    entry.getValue().applyEffectTick(level, pig, 0);
                    effects++;
                  }
                check(effects == 35, "all 35 effect callbacks execute");
                pig.discard();
                top.bearcabbage.annoyingeffects.effect.TanglingNightmareStatusEffect.AnnoyingBar
                    .set(player, 1.0);
                AnnoyingEffects.TANGLING_NIGHTMARE.value().applyEffectTick(level, player, 0);
                check(
                    ((top.bearcabbage.annoyingeffects.StatusEffectInstanceStackHolder) player)
                            .popStatusEffect()
                        != null,
                    "full nightmare buildup queues eligible effect");
                player.addEffect(new MobEffectInstance(AnnoyingEffects.CONTROLS_CRAB, 80));
                top.bearcabbage.lanterninstorm.network.StormNetworking.bar(player, true, 3);
                BeginningLanternEntity.create(level, pos.offset(0, 1, 2), "起点灯测试", true);
              });
      context.waitTicks(10);
      world.getClientLevel().waitForChunksRender();
      context.runOnClient(
          client -> {
            check(
                client.player != null && client.player.hasEffect(AnnoyingEffects.CONTROLS_CRAB),
                "effect synchronized to client");
            check(client.level != null, "client level loaded");
            var player = client.player;
            var oldInput = player.input;
            try {
              player.input =
                  new net.minecraft.client.player.ClientInput() {
                    @Override
                    public net.minecraft.world.phys.Vec2 getMoveVector() {
                      return new net.minecraft.world.phys.Vec2(.5F, .5F);
                    }
                  };
              player.addEffect(new MobEffectInstance(AnnoyingEffects.CONTROLS_MIRROR, 80));
              player.addEffect(new MobEffectInstance(AnnoyingEffects.CONTROLS_ALWAYS_JUMP, 80));
              player.applyInput();
              check(
                  player.zza == 0 && player.xxa < 0,
                  "client crab and mirror input modify movement");
              check(player.isJumping(), "client automatic jump");
              player.addEffect(new MobEffectInstance(AnnoyingEffects.REALLY_COLD, 80));
              player.applyInput();
              check(!player.isJumping(), "cold suppresses automatic jump");
              player.removeEffect(AnnoyingEffects.CONTROLS_MIRROR);
              player.removeEffect(AnnoyingEffects.CONTROLS_ALWAYS_JUMP);
              player.removeEffect(AnnoyingEffects.REALLY_COLD);
            } finally {
              player.input = oldInput;
            }
          });
      context.takeScreenshot("storm-lamps-and-hud");
      world
          .getServer()
          .runOnServer(
              server -> {
                var p = server.getPlayerList().getPlayers().getFirst();
                p.removeAllEffects();
                PlayerStore.save(p);
              });
    }
    try (var world = save.open()) {
      world
          .getServer()
          .runOnServer(
              server -> {
                var p = server.getPlayerList().getPlayers().getFirst();
                var state = PlayerStore.get(p);
                check(state.count("food", "test:food") == 2, "food progress survives restart");
                check(state.count("biome", "test:biome") == 1, "biome progress survives restart");
                check(state.spawn != null, "origin survives restart");
              });
    }
  }

  private static void verifyDefenseSelection(net.minecraft.server.level.ServerPlayer player) {
    var inventory =
        eu.pb4.trinkets.api.TrinketsApi.getAttachment(player).getInventory("offhand/glove");
    check(inventory != null && inventory.isEmpty(), "empty glove fixture available");
    var mainHand = player.getMainHandItem();
    var offHand = player.getOffhandItem();
    var gameplay = eu.pb4.trinkets.impl.TrinketsConfig.getGameplay(false);
    boolean cosmeticsEnabled = gameplay.cosmeticSlots;
    var extraSlots = Identifier.parse("storm_test:defense_selection");
    try {
      gameplay.cosmeticSlots = true;
      inventory.addSlotCountModifier(
          new net.minecraft.world.entity.ai.attributes.AttributeModifier(
              extraSlots,
              2,
              net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
      inventory.updateSlotCount();
      int last = inventory.getContainerSize() - 1;
      check(last >= 2, "multiple glove slots available");
      var first = new ItemStack(StormContent.FLASHLIGHT);
      var selected = new ItemStack(StormContent.TALISMAN);
      inventory.setItem(0, first);
      inventory.setItem(last, selected);
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, first.copy());
      player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, selected.copy());
      check(DefenseEquipment.find(player).stack() == selected, "last glove takes priority over hands");
      var replacement = new ItemStack(StormContent.FLASHLIGHT);
      DefenseEquipment.find(player).replace().accept(replacement);
      check(
          inventory.getItem(last) == replacement && inventory.getItem(0) == first,
          "glove replacement changes only the selected slot");
      check(inventory.hasCosmeticItems(), "cosmetic glove fixture available");
      inventory.setCosmeticItem(0, selected);
      check(
          DefenseEquipment.find(player).stack() == selected,
          "cosmetic glove takes priority over all ordinary glove slots");
      var lastCosmetic = first.copy();
      inventory.setCosmeticItem(last, lastCosmetic);
      check(
          DefenseEquipment.find(player).stack() == lastCosmetic,
          "last cosmetic glove takes priority");
      var cosmeticReplacement = new ItemStack(StormContent.TALISMAN);
      DefenseEquipment.find(player).replace().accept(cosmeticReplacement);
      check(
          inventory.getCosmeticItem(last) == cosmeticReplacement
              && inventory.getItem(last) == replacement,
          "cosmetic replacement leaves ordinary glove untouched");
      for (int i = 0; i < inventory.getContainerSize(); i++) {
        inventory.setItem(i, ItemStack.EMPTY);
        inventory.setCosmeticItem(i, ItemStack.EMPTY);
      }
      check(
          DefenseEquipment.find(player).stack() == player.getMainHandItem(),
          "main hand takes priority over offhand when gloves are empty");
      DefenseEquipment.find(player).replace().accept(replacement);
      check(player.getMainHandItem() == replacement, "main-hand replacement targets main hand");
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
      check(
          DefenseEquipment.find(player).stack() == player.getOffhandItem(),
          "non-defense main hand falls back to offhand");
      DefenseEquipment.find(player).replace().accept(cosmeticReplacement);
      check(player.getOffhandItem() == cosmeticReplacement, "offhand replacement targets offhand");
      player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
      var empty = DefenseEquipment.find(player);
      check(empty.stack().isEmpty(), "missing defense returns empty stack");
      empty.replace().accept(replacement);
      check(
          player.getMainHandItem().is(Items.STONE) && player.getOffhandItem().isEmpty(),
          "empty defense replacement leaves both hands untouched");
    } finally {
      for (int i = 0; i < inventory.getContainerSize(); i++) {
        inventory.setItem(i, ItemStack.EMPTY);
        inventory.setCosmeticItem(i, ItemStack.EMPTY);
      }
      gameplay.cosmeticSlots = cosmeticsEnabled;
      inventory.removeSlotCountModifier(extraSlots);
      inventory.updateSlotCount();
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, mainHand);
      player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, offHand);
    }
  }

  private static void verifyLampPlacement(net.minecraft.server.level.ServerPlayer player) {
    var originalHand = player.getMainHandItem();
    try {
      for (String dimension :
          List.of("mirrortree:bedroom", "starry_skies:placement_test", "storm_test:nightmare")) {
        var key =
            net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, Identifier.parse(dimension));
        var level = player.level().getServer().getLevel(key);
        check(level != null, "lamp placement fixture dimension loads: " + dimension);
        player.teleportTo(level, 4.5, 8, 6.5, Set.of(), 0, 0, false);
        BlockPos target = new BlockPos(4, 8, 4);
        level.setBlockAndUpdate(
            target.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        for (var entry : StormContent.LAMPS.entrySet()) {
          var lamp = entry.getValue();
          boolean allowed = true;
          level.setBlockAndUpdate(
              target, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
          var stack = new ItemStack(lamp.asItem());
          player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
          var hit =
              new net.minecraft.world.phys.BlockHitResult(
                  Vec3.atCenterOf(target), net.minecraft.core.Direction.UP, target, false);
          var context =
              new net.minecraft.world.item.context.BlockPlaceContext(
                  player, net.minecraft.world.InteractionHand.MAIN_HAND, stack, hit);
          if (stack.getItem() instanceof BlockItem item) {
            int before = stack.getCount();
            var result = item.place(context);
            check(
                result.consumesAction() == allowed,
                "actual block item placement " + dimension + " " + entry.getKey());
            check(
                level.getBlockState(target).is(lamp) == allowed,
                "placement changes block only when permitted");
            check(
                stack.getCount() == (allowed ? before - 1 : before),
                "rejected lamp placement preserves item count");
          } else
            check(lamp.getStateForPlacement(context) != null, "white-paper block placement supported");
        }
        // Existing lamps also survive neighbor updates.
        var existing = StormContent.LAMPS.get("deathbear_lantern").defaultBlockState();
        level.setBlockAndUpdate(target, existing);
        level.updateNeighborsAt(target.below(), net.minecraft.world.level.block.Blocks.STONE);
        check(
            level.getBlockState(target).is(existing.getBlock()),
            "existing commemorative lantern remains in " + dimension);
      }
    } finally {
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, originalHand);
    }
  }

  private static void verifyBedroomSafety(net.minecraft.server.level.ServerPlayer player) {
    var server = player.level().getServer();
    var bedroom =
        server.getLevel(
            net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                Identifier.parse("mirrortree:bedroom")));
    check(bedroom != null, "bedroom safety fixture loads");
    LanternInStormAPI.safeWorlds.clear();
    player.teleportTo(bedroom, 100.5, 8, 100.5, Set.of(), 0, 0, false);
    var state = PlayerStore.get(player);
    state.graceTicks = 0;
    state.safe = true; // Login starts safe even with persisted nightmare effects.
    state.cleared = false;
    state.tick = 19;
    var glove =
        eu.pb4.trinkets.api.TrinketsApi.getAttachment(player).getSlotAccess("offhand/glove", 0);
    glove.set(ItemStack.EMPTY);
    player.addEffect(new MobEffectInstance(AnnoyingEffects.TANGLING_NIGHTMARE, 200));
    player.addEffect(new MobEffectInstance(AnnoyingEffects.TANGLING_DREAMS, 200));
    var bar = top.bearcabbage.annoyingeffects.effect.TanglingNightmareStatusEffect.AnnoyingBar;
    bar.set(player, .75);
    SafetyService.tick(player);
    check(state.safe, "bedroom is safe without lamp, origin, grace period or API registration");
    check(
        !player.hasEffect(AnnoyingEffects.TANGLING_NIGHTMARE)
            && !player.hasEffect(AnnoyingEffects.TANGLING_DREAMS),
        "entering bedroom clears both nightmare types");
    check(bar.get(player) == 0, "bedroom resets nightmare buildup");
    for (var item : List.of(StormContent.FLASHLIGHT, StormContent.TALISMAN)) {
      var stack = new ItemStack(item);
      stack.setDamageValue(50);
      glove.set(stack);
      for (int second = 0; second < 5; second++) {
        state.tick = 19;
        SafetyService.tick(player);
      }
      check(
          state.safe && stack.getDamageValue() == 50,
          "bedroom safety does not consume defense durability");
      check(
          !player.hasEffect(AnnoyingEffects.TANGLING_NIGHTMARE)
              && !player.hasEffect(AnnoyingEffects.TANGLING_DREAMS),
          "bedroom never applies ambient nightmares");
    }
    glove.set(ItemStack.EMPTY);
    var stars =
        server.getLevel(
            net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                Identifier.parse("starry_skies:placement_test")));
    check(stars != null, "starry_skies fixture loads");
    player.teleportTo(stars, 100.5, 8, 100.5, Set.of(), 0, 0, false);
    state.tick = 19;
    SafetyService.tick(player);
    check(
        !state.safe && player.hasEffect(AnnoyingEffects.TANGLING_NIGHTMARE),
        "leaving bedroom for starry_skies restores ordinary nightmare rules");
    player.removeAllEffects();
  }
}
