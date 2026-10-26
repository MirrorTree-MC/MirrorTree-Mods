package top.bearcabbage.lanterninstorm.test;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.server.players.NameAndId;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.Vec3;
import top.bearcabbage.annoyingeffects.effect.TanglingNightmareStatusEffect;
import top.bearcabbage.lanterninstorm.content.StormContent;
import top.bearcabbage.mirrorskills.util.StatusEffectInstanceExtensions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;
import top.bearcabbage.lanterninstorm.LanternInStormAPI;
import top.bearcabbage.lanterninstorm.progression.PlayerStore;
import top.bearcabbage.mirrorskills.MirrorSkills;
import top.bearcabbage.mirrorskills.data.SkillData;

public class ReviewRegressionGameTest {
  @GameTest(maxTicks = 40)
  public void crossDimensionSpawnpoint(GameTestHelper helper) throws Exception {
    var server = helper.getLevel().getServer();
    var player = StormServerGameTest.player(server.overworld(), "spawn-review");
    var target = server.getLevel(Level.NETHER);
    try {
      server.getCommands().getDispatcher().execute("spawnpoint spawn-review 100 100 100",
          server.createCommandSourceStack().withLevel(target));
      helper.assertTrue(player.getRespawnConfig().respawnData().globalPos().dimension().equals(Level.NETHER),
          "vanilla respawn follows the command dimension");
      helper.assertTrue(PlayerStore.get(player).spawnDimension.equals("minecraft:the_nether"),
          "LIStorm origin follows the command dimension");
      LanternInStormAPI.setRTPSpawn(player, new BlockPos(101, 100, 101), false);
      helper.assertTrue(PlayerStore.get(player).spawnDimension.equals("minecraft:overworld"),
          "existing API still uses the player's world");
      helper.succeed();
    } finally {
      server.getPlayerList().remove(player);
    }
  }

  @GameTest(maxTicks = 40)
  public void waterEffectRemovalDoesNotInterruptTick(GameTestHelper helper) {
    var player = StormServerGameTest.player(helper.getLevel(), "water-review");
    try {
      player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 100));
      player.addEffect(new MobEffectInstance(AnnoyingEffects.WATER_FILLING, 1));
      player.addEffect(new MobEffectInstance(MobEffects.SPEED, 100));
      player.doTick();
      helper.assertTrue(!player.hasEffect(MobEffects.CONDUIT_POWER), "water still removes conduit power");
      helper.assertTrue(!player.hasEffect(AnnoyingEffects.WATER_FILLING), "expired water is removed in the same tick");
      helper.assertTrue(player.getEffect(MobEffects.SPEED).getDuration()==99, "other effects tick normally");
      helper.succeed();
    } finally {
      helper.getLevel().getServer().getPlayerList().remove(player);
    }
  }

  @GameTest(maxTicks = 100)
  public void bedroomProtectsBeforeFirstEffectTick(GameTestHelper helper) {
    var bedroom = helper.getLevel().getServer().getLevel(
        ResourceKey.create(Registries.DIMENSION, Identifier.parse("mirrortree:bedroom")));
    helper.assertTrue(bedroom != null, "bedroom fixture loads");
    for (GameType mode : GameType.values()) {
      var player = StormServerGameTest.player(helper.getLevel(), "bedroom-" + mode.getId());
      try {
        player.setGameMode(mode);
        addBedroomEffects(player);
        var pos = new BlockPos(256 + mode.getId() * 32, 80, 256);
        helper.assertTrue(player.teleportTo(bedroom, pos.getX() + .5, pos.getY(), pos.getZ() + .5,
            Set.of(), 0, 0, false), "ordinary cross-dimension teleport succeeds");
        var state = PlayerStore.get(player);
        state.tick = 0;
        state.safe = state.cleared = true;
        verifyBedroomTick(helper, player, pos);
        // New effects must also be removed after the initial safe/cleared transition.
        addBedroomEffects(player);
        verifyBedroomTick(helper, player, pos);
      } finally {
        helper.getLevel().getServer().getPlayerList().remove(player);
      }
    }
    helper.succeed();
  }

  @GameTest(maxTicks = 100)
  public void bedroomProtectsPersistedEffectsOnLogin(GameTestHelper helper) {
    var server = helper.getLevel().getServer();
    var bedroom = server.getLevel(
        ResourceKey.create(Registries.DIMENSION, Identifier.parse("mirrortree:bedroom")));
    var player = StormServerGameTest.player(helper.getLevel(), "bedroom-login");
    var profile = player.getGameProfile();
    var pos = new BlockPos(416, 80, 256);
    try {
      helper.assertTrue(player.teleportTo(bedroom, pos.getX() + .5, pos.getY(), pos.getZ() + .5,
          Set.of(), 0, 0, false), "login fixture enters bedroom");
      addBedroomEffects(player);
    } finally {
      server.getPlayerList().remove(player); // Save effects and dimension through the real logout path.
    }
    var savedNbt = server.getPlayerList().loadPlayerData(new NameAndId(profile));
    helper.assertTrue(savedNbt.isPresent(), "logout writes player NBT for the returning profile");
    // Follow PrepareSpawnTask's NBT loading path, without asynchronous spawn-chunk scheduling.
    var problems = new ProblemReporter.Collector();
    var input = TagValueInput.create(problems, server.registryAccess(), savedNbt.orElseThrow());
    var savedPosition = input.read(ServerPlayer.SavedPosition.MAP_CODEC).orElseThrow();
    var savedLevel = server.getLevel(savedPosition.dimension().orElseThrow());
    helper.assertTrue(savedLevel == bedroom, "saved player NBT records the bedroom dimension");
    var cookie = CommonListenerCookie.createInitial(profile, false);
    var restored = new ServerPlayer(server, savedLevel, profile, cookie.clientInformation());
    restored.load(input);
    var rotation = savedPosition.rotation().orElseThrow();
    restored.snapTo(savedPosition.position().orElseThrow(), rotation.x, rotation.y);
    helper.assertTrue(problems.isEmpty(), "saved player NBT loads without codec errors: " + problems.getReport());
    var connection = new Connection(PacketFlow.SERVERBOUND);
    new EmbeddedChannel(connection);
    server.getPlayerList().placeNewPlayer(connection, restored, cookie);
    try {
      helper.assertTrue(restored.level() == bedroom && restored.hasEffect(AnnoyingEffects.WATER_FILLING),
          "login restores the bedroom and existing destructive effect before any tick");
      helper.assertTrue(PlayerStore.get(restored).tick == 0 && !PlayerStore.get(restored).cleared,
          "login has not reached the periodic safety check");
      verifyBedroomTick(helper, restored, pos);
      helper.succeed();
    } finally {
      server.getPlayerList().remove(restored);
    }
  }

  @GameTest(maxTicks = 40)
  public void destructiveEffectsStillTickOutsideBedroom(GameTestHelper helper) {
    var player = StormServerGameTest.player(helper.getLevel(), "bedroom-outside");
    try {
      var pos = helper.absolutePos(new BlockPos(2, 3, 2));
      player.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
      player.setNoGravity(true);
      player.setDeltaMovement(Vec3.ZERO);
      helper.getLevel().setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
      player.addEffect(new MobEffectInstance(AnnoyingEffects.WATER_FILLING, 200));
      player.doTick();
      helper.assertTrue(player.hasEffect(AnnoyingEffects.WATER_FILLING)
              && helper.getLevel().getBlockState(pos.above()).is(Blocks.WATER),
          "ordinary dimensions retain destructive effect ticking before periodic safety");
      helper.succeed();
    } finally {
      helper.getLevel().getServer().getPlayerList().remove(player);
    }
  }

  private static void addBedroomEffects(ServerPlayer player) {
    player.addEffect(new MobEffectInstance(AnnoyingEffects.WATER_FILLING, 200));
    player.addEffect(new MobEffectInstance(AnnoyingEffects.CHAOTIC_TELEPORTATION, 200, 9));
    player.addEffect(new MobEffectInstance(AnnoyingEffects.TANGLING_NIGHTMARE, 200));
    var speed = new MobEffectInstance(MobEffects.SPEED, 200);
    var flags = (StatusEffectInstanceExtensions) speed;
    flags.mirrorskills$setDurationModified(true);
    flags.mirrorskills$setLevelModified(true);
    player.addEffect(speed);
    var defense = new ItemStack(StormContent.FLASHLIGHT);
    defense.setDamageValue(40);
    player.setItemInHand(InteractionHand.MAIN_HAND, defense);
    TanglingNightmareStatusEffect.AnnoyingBar.set(player, .75);
  }

  private static void verifyBedroomTick(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
    ServerLevel level = player.level();
    level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(pos.east().above(), Blocks.FARMLAND.defaultBlockState());
    player.setNoGravity(true);
    player.setDeltaMovement(Vec3.ZERO);
    Vec3 before = player.position();
    var speed = player.getEffect(MobEffects.SPEED);
    int duration = speed.getDuration();
    player.doTick();
    helper.assertTrue(player.position().distanceToSqr(before) < 1.0e-8,
        "existing chaotic teleportation cannot move a bedroom player");
    helper.assertTrue(level.getBlockState(pos.above()).isAir()
            && level.getBlockState(pos.east().above()).is(Blocks.FARMLAND),
        "existing water filling cannot flood or damage the bedroom");
    helper.assertTrue(player.getActiveEffects().stream().allMatch(e -> e.getEffect().value().isBeneficial()),
        "bedroom removes existing debuffs in every game mode before they execute");
    var flags = (StatusEffectInstanceExtensions) player.getEffect(MobEffects.SPEED);
    helper.assertTrue(player.getEffect(MobEffects.SPEED) == speed && speed.getDuration() == duration - 1
            && flags.mirrorskills$isDurationModified() && flags.mirrorskills$isLevelModified(),
        "beneficial effects tick normally and preserve skill markers");
    helper.assertTrue(player.getMainHandItem().getDamageValue() == 40
            && PlayerStore.get(player).tick == 0
            && TanglingNightmareStatusEffect.AnnoyingBar.get(player) == 0,
        "bedroom protection consumes no durability and resets buildup before the 20-tick check");
  }

  @GameTest(maxTicks = 40)
  public void waterFillingPreservesBlockRules(GameTestHelper helper) {
    var level = helper.getLevel();
    var player = StormServerGameTest.player(level, "water-blocks");
    var origin = helper.absolutePos(new BlockPos(4, 4, 4));
    try {
      player.teleportTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
      var center = origin.above();
      level.setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
      level.setBlockAndUpdate(center.east(), Blocks.OAK_LEAVES.defaultBlockState());
      level.setBlockAndUpdate(center.west(), Blocks.DIRT_PATH.defaultBlockState());
      level.setBlockAndUpdate(center.north(), Blocks.SOUL_SAND.defaultBlockState());
      level.setBlockAndUpdate(center.south(), Blocks.STONE.defaultBlockState());
      var outside = origin.offset(3, 3, 3);
      level.setBlockAndUpdate(outside, Blocks.OAK_LEAVES.defaultBlockState());
      AnnoyingEffects.WATER_FILLING.value().applyEffectTick(level, player, 0);
      helper.assertTrue(level.getBlockState(center).is(Blocks.WATER)
              && level.getBlockState(center).getValue(LiquidBlock.LEVEL) == 8,
          "air becomes the original flowing-water state");
      helper.assertTrue(level.getBlockState(center.east()).is(Blocks.WATER),
          "leaf destruction is followed by water placement at the same position");
      helper.assertTrue(level.getBlockState(center.west()).is(Blocks.DIRT), "paths become dirt");
      helper.assertTrue(level.getBlockState(center.north()).is(Blocks.SOUL_SAND)
              && level.getBlockState(center.south()).is(Blocks.STONE),
          "soul sand and full solid blocks remain intact");
      helper.assertTrue(level.getBlockState(outside).is(Blocks.OAK_LEAVES),
          "the cubic loop still excludes positions outside the eye-centered sphere");
      AnnoyingEffects.WATER_FILLING.value().applyEffectTick(level, player, 0);
      helper.assertTrue(level.getBlockState(center).is(Blocks.WATER)
              && level.getBlockState(center.north()).is(Blocks.SOUL_SAND),
          "repeated scans do not disturb existing fluid or excluded blocks");
      helper.succeed();
    } finally {
      level.getServer().getPlayerList().remove(player);
    }
  }

  @GameTest(maxTicks = 40)
  public void effectTickSchedulesRemainDistinct(GameTestHelper helper) {
    var passive = Set.of("anorexia", "controls_always_jump", "controls_chaotic_use",
        "controls_crab", "controls_mirror", "controls_short_reach", "curse_of_vanishing",
        "disable_crafting_table", "disable_inventory", "heaviness", "misfortune", "repeater",
        "schizophrenia", "slippy", "voiceless", "vulnerable");
    int registered = 0;
    for (var entry : BuiltInRegistries.MOB_EFFECT.entrySet()) {
      var id = entry.getKey().identifier();
      if (!id.getNamespace().equals("annoyingeffects")) continue;
      registered++;
      String path = id.getPath();
      if (path.equals("chaotic_teleportation") || path.equals("swapping_hands")) continue;
      for (int duration : new int[] {1, 20, 37})
        helper.assertTrue(entry.getValue().shouldApplyEffectTickThisTick(duration, 2) == !passive.contains(path),
            "passive and always-ticking effects retain distinct schedules: " + path);
    }
    helper.assertTrue(registered == 35, "all 35 effect identities remain registered");
    var teleport = AnnoyingEffects.CHAOTIC_TELEPORTATION.value();
    helper.assertTrue(teleport.shouldApplyEffectTickThisTick(10, 0)
        && !teleport.shouldApplyEffectTickThisTick(9, 0)
        && teleport.shouldApplyEffectTickThisTick(1, 9), "teleport keeps its amplifier-dependent interval");
    var swap = AnnoyingEffects.SWAPPING_HANDS.value();
    helper.assertTrue(swap.shouldApplyEffectTickThisTick(20, 0)
        && !swap.shouldApplyEffectTickThisTick(19, 0)
        && swap.shouldApplyEffectTickThisTick(2, 4)
        && !swap.shouldApplyEffectTickThisTick(1, 4), "hand swapping keeps its independent interval");
    helper.succeed();
  }

  @GameTest(maxTicks = 100)
  public void skillCommandsPreserveSnapshotsAndResetOnlyOwnedParameters(GameTestHelper helper) throws Exception {
    var player = StormServerGameTest.player(helper.getLevel(), "command-review");
    var server = helper.getLevel().getServer();
    var target = (AttachmentTarget) player;
    var parameters = List.of("beneficial_increase", "harmful_reduction", "level_adjustment",
        "crit_multiplier", "periodic_timer", "invincible_cd");
    try {
      for (String skill : List.of(SkillData.SKILL_POTION_TIME, SkillData.SKILL_POTION_LEVEL,
          SkillData.SKILL_CRIT_MULTIPLIER, SkillData.SKILL_PERIODIC_ABSORPTION,
          SkillData.SKILL_INVISIBILITY, "custom_skill")) {
        var original = new SkillData();
        for (String parameter : parameters) original.setParameter(parameter, 7);
        target.setAttached(MirrorSkills.SKILL_DATA, original);
        server.getCommands().getDispatcher().execute("mirrorskills grant command-review " + skill,
            server.createCommandSourceStack());
        var granted = target.getAttachedOrCreate(MirrorSkills.SKILL_DATA);
        helper.assertTrue(granted.isSkillEnabled(skill) && !original.isSkillEnabled(skill),
            "grant publishes a copy without altering prior snapshots");
        helper.assertTrue(granted.getParameter("periodic_timer") ==
            (skill.equals(SkillData.SKILL_PERIODIC_ABSORPTION) ? 12000 : 7),
            "only periodic absorption starts immediately on grant");
        server.getCommands().getDispatcher().execute("mirrorskills revoke command-review " + skill,
            server.createCommandSourceStack());
        var revoked = target.getAttachedOrCreate(MirrorSkills.SKILL_DATA);
        helper.assertTrue(!revoked.isSkillEnabled(skill) && granted.isSkillEnabled(skill),
            "revoke publishes a copy without altering prior snapshots");
        for (String parameter : parameters) {
          boolean reset = skill.equals(SkillData.SKILL_POTION_TIME)
                  && (parameter.equals("beneficial_increase") || parameter.equals("harmful_reduction"))
              || skill.equals(SkillData.SKILL_POTION_LEVEL) && parameter.equals("level_adjustment")
              || skill.equals(SkillData.SKILL_CRIT_MULTIPLIER) && parameter.equals("crit_multiplier")
              || skill.equals(SkillData.SKILL_PERIODIC_ABSORPTION) && parameter.equals("periodic_timer");
          helper.assertTrue(revoked.getParameter(parameter) == (reset ? 0 : 7),
              "revoke resets only the selected skill's parameters: " + skill + "/" + parameter);
        }
      }
      helper.succeed();
    } finally {
      server.getPlayerList().remove(player);
    }
  }

  @GameTest(maxTicks = 100)
  public void skillTriggersPreserveCooldownBoundariesAndSnapshots(GameTestHelper helper) throws Exception {
    var trigger = top.bearcabbage.mirrorskills.network.ModNetworking.class.getDeclaredMethod(
        "executeSkill", net.minecraft.server.level.ServerPlayer.class, String.class);
    trigger.setAccessible(true);
    record SkillCase(String skill, long ticks, float invincibleSeconds) {}
    var cases = List.of(new SkillCase(SkillData.SKILL_INVISIBILITY, 1200, 0),
        new SkillCase(SkillData.SKILL_DASHING, 20, 0),
        new SkillCase(SkillData.SKILL_INVINCIBLE, 2400, 0),
        new SkillCase(SkillData.SKILL_INVINCIBLE, 40, 2),
        new SkillCase(SkillData.SKILL_TIPPED_ARROW, 6000, 0));
    for (int i = 0; i < cases.size(); i++) {
      var sample = cases.get(i);
      var player = StormServerGameTest.player(helper.getLevel(), "trigger-review" + i);
      var target = (AttachmentTarget) player;
      try {
        var locked = new SkillData();
        locked.setSkillEnabled("custom_skill", true);
        target.setAttached(MirrorSkills.SKILL_DATA, locked);
        trigger.invoke(null, player, sample.skill());
        trigger.invoke(null, player, "custom_skill");
        helper.assertTrue(target.getAttachedOrCreate(MirrorSkills.SKILL_DATA) == locked,
            "locked and unknown skills do not publish snapshots");

        long now = player.level().getGameTime();
        var blocked = new SkillData();
        blocked.setSkillEnabled(sample.skill(), true);
        blocked.setParameter("invincible_cd", sample.invincibleSeconds());
        blocked.setParameter("invincible_ticks", 7);
        blocked.setParameter("unrelated", 9);
        blocked.setCooldown(sample.skill(), now + 1);
        target.setAttached(MirrorSkills.SKILL_DATA, blocked);
        trigger.invoke(null, player, sample.skill());
        helper.assertTrue(target.getAttachedOrCreate(MirrorSkills.SKILL_DATA) == blocked,
            "a future cooldown timestamp rejects activation: " + sample.skill());

        var ready = blocked.copy();
        ready.setCooldown(sample.skill(), now - sample.ticks());
        target.setAttached(MirrorSkills.SKILL_DATA, ready);
        player.setYRot(0);
        player.setXRot(0);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        trigger.invoke(null, player, sample.skill());
        var activated = target.getAttachedOrCreate(MirrorSkills.SKILL_DATA);
        helper.assertTrue(activated != ready && activated.getCooldown(sample.skill()) == now
                && ready.getCooldown(sample.skill()) == now - sample.ticks(),
            "exact cooldown boundary activates and preserves the previous snapshot: " + sample.skill());
        helper.assertTrue(activated.getParameter("unrelated") == 9
                && activated.getParameter("invincible_ticks") ==
                    (sample.skill().equals(SkillData.SKILL_INVINCIBLE) ? 100 : 7)
                && ready.getParameter("invincible_ticks") == 7,
            "activation changes only its own parameters: " + sample.skill());
        if (sample.skill().equals(SkillData.SKILL_INVISIBILITY))
          helper.assertTrue(player.getEffect(MobEffects.INVISIBILITY).getDuration() == 100,
              "invisibility lasts five seconds");
        if (sample.skill().equals(SkillData.SKILL_DASHING))
          helper.assertTrue(player.getDeltaMovement().distanceToSqr(new net.minecraft.world.phys.Vec3(0, .4, 2)) < 1.0e-10,
              "dash retains its horizontal and vertical impulse");
        if (sample.skill().equals(SkillData.SKILL_TIPPED_ARROW))
          helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.TIPPED_ARROW) == 16,
              "tipped-arrow activation gives sixteen arrows");

        var neverUsed = activated.copy();
        neverUsed.setCooldown(sample.skill(), 0);
        target.setAttached(MirrorSkills.SKILL_DATA, neverUsed);
        trigger.invoke(null, player, sample.skill());
        helper.assertTrue(target.getAttachedOrCreate(MirrorSkills.SKILL_DATA) != neverUsed
                && neverUsed.getCooldown(sample.skill()) == 0,
            "zero remains the unused-cooldown sentinel: " + sample.skill());
      } finally {
        helper.getLevel().getServer().getPlayerList().remove(player);
      }
    }
    helper.succeed();
  }
}
