package top.bearcabbage.lanterninstorm.test;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import top.bearcabbage.mirrorskills.MirrorSkills;
import top.bearcabbage.mirrorskills.data.SkillData;
import top.bearcabbage.mirrorskills.util.StatusEffectInstanceExtensions;

public class SkillsRegressionGameTest {
  private static StatusEffectInstanceExtensions flags(MobEffectInstance effect) {
    return (StatusEffectInstanceExtensions) effect;
  }

  private static MobEffectInstance marked(int duration, int amplifier, boolean time, boolean level) {
    var effect = new MobEffectInstance(MobEffects.REGENERATION, duration, amplifier);
    flags(effect).mirrorskills$setDurationModified(time);
    flags(effect).mirrorskills$setLevelModified(level);
    return effect;
  }

  private static SkillData skills(ServerPlayer player) {
    return ((AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA);
  }

  private static void publish(ServerPlayer player, SkillData data) {
    ((AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy());
  }

  private static void potionSkills(ServerPlayer player, float harmfulReduction) {
    var data = skills(player);
    data.setSkillEnabled(SkillData.SKILL_POTION_TIME, true);
    data.setSkillEnabled(SkillData.SKILL_POTION_LEVEL, true);
    data.setParameter("beneficial_increase", .5f);
    data.setParameter("harmful_reduction", harmfulReduction);
    data.setParameter("level_adjustment", 1);
    publish(player, data);
  }

  @GameTest(maxTicks = 200)
  public void refreshPreservesFieldProvenance(GameTestHelper helper) {
    var player = StormServerGameTest.player(helper.getLevel(), "effect-refresh");
    try {
      player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
      potionSkills(player, .5f);
      player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
      var active = player.getEffect(MobEffects.REGENERATION);
      var saved = MobEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE, new MobEffectInstance(active)).getOrThrow();
      var restored = MobEffectInstance.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
      player.removeAllEffects();
      player.addEffect(restored);
      active = player.getEffect(MobEffects.REGENERATION);
      helper.assertTrue(active.getDuration() == 300 && active.getAmplifier() == 1,
          "refresh then save/restore must not apply potion bonuses twice");

      var current = marked(100, 1, false, true);
      current.update(marked(200, 1, true, false));
      helper.assertTrue(flags(current).mirrorskills$isDurationModified()
              && flags(current).mirrorskills$isLevelModified(),
          "equal-level extension replaces only the duration marker");
      current.update(marked(300, 1, false, false));
      helper.assertTrue(!flags(current).mirrorskills$isDurationModified()
              && flags(current).mirrorskills$isLevelModified(),
          "replacement duration may clear its marker without clearing the level marker");
      current.update(marked(50, 0, true, false));
      helper.assertTrue(!flags(current).mirrorskills$isDurationModified()
              && flags(current).mirrorskills$isLevelModified(),
          "rejected weaker effect does not change current markers");
      current.update(marked(50, 2, true, false));
      helper.assertTrue(flags(current).mirrorskills$isDurationModified()
              && !flags(current).mirrorskills$isLevelModified(),
          "stronger effect adopts both source markers, including false");
      helper.succeed();
    } finally {
      helper.getLevel().getServer().getPlayerList().remove(player);
    }
  }

  @GameTest(maxTicks = 200)
  public void hiddenMarkersSurviveStorageAndPromotion(GameTestHelper helper) {
    var player = StormServerGameTest.player(helper.getLevel(), "hidden-effects");
    try {
      var hidden = marked(300, 0, true, false);
      var middle = new MobEffectInstance(MobEffects.REGENERATION, 2, 1, false, true, true, hidden);
      flags(middle).mirrorskills$setLevelModified(true);
      var head = new MobEffectInstance(MobEffects.REGENERATION, 1, 2, false, true, true, middle);
      flags(head).mirrorskills$setDurationModified(true);
      flags(head).mirrorskills$setLevelModified(true);
      var json = MobEffectInstance.CODEC.encodeStart(JsonOps.INSTANCE, head).getOrThrow();
      var restored = MobEffectInstance.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
      var nbt = MobEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE, restored).getOrThrow();
      restored = MobEffectInstance.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
      restored.tickServer(player.level(), player, () -> {});
      helper.assertTrue(restored.getAmplifier() == 1
              && !flags(restored).mirrorskills$isDurationModified()
              && flags(restored).mirrorskills$isLevelModified(),
          "hidden promotion replaces both markers with the middle layer's markers");
      restored.tickServer(player.level(), player, () -> {});
      helper.assertTrue(restored.getAmplifier() == 0
              && flags(restored).mirrorskills$isDurationModified()
              && !flags(restored).mirrorskills$isLevelModified(),
          "nested hidden markers survive JSON/NBT and promotion");

      potionSkills(player, .5f);
      var incoming = new MobEffectInstance(MobEffects.REGENERATION, 10, 2, false, false, false,
          marked(300, 0, true, true));
      player.addEffect(incoming);
      var active = player.getEffect(MobEffects.REGENERATION);
      var snapshot = MobEffectInstance.CODEC.encodeStart(JsonOps.INSTANCE, active).getOrThrow();
      player.removeAllEffects();
      player.addEffect(MobEffectInstance.CODEC.parse(JsonOps.INSTANCE, snapshot).getOrThrow());
      active = player.getEffect(MobEffects.REGENERATION);
      helper.assertTrue(active.getDuration() == 15 && active.getAmplifier() == 3
              && !active.isVisible() && !active.showIcon(), "replacement preserves modified head and display flags");
      for (int i = 0; i < 15; i++) active.tickServer(player.level(), player, () -> {});
      helper.assertTrue(active.getAmplifier() == 0 && active.getDuration() == 285
              && flags(active).mirrorskills$isDurationModified()
              && flags(active).mirrorskills$isLevelModified(),
          "skill adjustment and snapshot restore retain the hidden effect chain");
      helper.succeed();
    } finally {
      helper.getLevel().getServer().getPlayerList().remove(player);
    }
  }

  @GameTest(maxTicks = 200)
  public void infiniteDurationIsNotScaled(GameTestHelper helper) {
    var player = StormServerGameTest.player(helper.getLevel(), "infinite-effects");
    try {
      for (float reduction : new float[]{0, .5f, 1}) {
        potionSkills(player, reduction);
        for (Holder<MobEffect> type : List.of(MobEffects.REGENERATION, MobEffects.POISON)) {
          player.removeAllEffects();
          player.addEffect(new MobEffectInstance(type, MobEffectInstance.INFINITE_DURATION, 2));
          var effect = player.getEffect(type);
          helper.assertTrue(effect.isInfiniteDuration(), "infinite sentinel survives duration adjustment");
          helper.assertTrue(effect.getAmplifier() == (type == MobEffects.POISON ? 1 : 3),
              "infinite effects still receive level adjustment");
          effect.tickServer(player.level(), player, () -> {});
          helper.assertTrue(effect.isInfiniteDuration(), "infinite effect persists after a tick");
          var saved = MobEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE, effect).getOrThrow();
          player.removeAllEffects();
          player.addEffect(MobEffectInstance.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow());
          helper.assertTrue(player.getEffect(type).isInfiniteDuration()
                  && player.getEffect(type).getAmplifier() == effect.getAmplifier(),
              "infinite effect survives save/restore without repeated level adjustment");
        }
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 2));
        helper.assertTrue(player.getEffect(MobEffects.POISON).getDuration() == (int) (200 * (1 - reduction)),
            "finite harmful effect retains the existing scaling formula");
      }
      helper.succeed();
    } finally {
      helper.getLevel().getServer().getPlayerList().remove(player);
    }
  }

  private static int flashback(ServerPlayer player) throws Exception {
    return player.level().getServer().getCommands().getDispatcher().execute("death", player.createCommandSourceStack());
  }

  private static void deathRecord(ServerPlayer player, Vec3 pos, String dimension) {
    var data = skills(player);
    data.setSkillEnabled(SkillData.SKILL_DEATH_FLASHBACK, true);
    data.setLastDeathLocation(pos, dimension);
    publish(player, data);
  }

  @GameTest(maxTicks = 200)
  public void invalidDeathDestinationRetainsRecord(GameTestHelper helper) throws Exception {
    var player = StormServerGameTest.player(helper.getLevel(), "invalid-death");
    try {
      Vec3 start = player.position();
      Vec3 destination = start.add(3, 1, 3);
      for (String dimension : new String[]{"storm_test:missing", "INVALID DIMENSION", ""}) {
        deathRecord(player, destination, dimension);
        helper.assertTrue(flashback(player) == 0 && skills(player).hasDeathLocation()
                && destination.equals(skills(player).getLastDeathLocation())
                && dimension.equals(skills(player).getLastDeathDimension()) && player.position().equals(start),
            "invalid/missing recorded dimension preserves position and record");
      }
      deathRecord(player, new Vec3(Double.NaN, 80, 0), Level.OVERWORLD.identifier().toString());
      helper.assertTrue(flashback(player) == 0 && skills(player).hasDeathLocation()
              && player.position().equals(start), "invalid coordinates cannot consume the death record");
      deathRecord(player, destination, null);
      helper.assertTrue(flashback(player) == 1 && !skills(player).hasDeathLocation()
              && player.position().distanceToSqr(destination) < 1.0e-8,
          "legacy record without a dimension still uses the current world");
      helper.assertTrue(flashback(player) == 0, "successful flashback is consumed exactly once");
      helper.succeed();
    } finally {
      helper.getLevel().getServer().getPlayerList().remove(player);
    }
  }

  @GameTest(maxTicks = 200)
  public void rejectedTeleportRetainsRecord(GameTestHelper helper) throws Exception {
    var player = StormServerGameTest.player(helper.getLevel(), "rejected-death");
    try {
      Vec3 destination = player.position().add(4, 0, 4);
      deathRecord(player, destination, player.level().dimension().identifier().toString());
      player.setRemoved(Entity.RemovalReason.DISCARDED); // Vanilla teleport returns false for removed players.
      helper.assertTrue(flashback(player) == 0 && skills(player).hasDeathLocation()
              && destination.equals(skills(player).getLastDeathLocation()),
          "teleport rejection preserves the complete death record");
      helper.succeed();
    } finally {
      helper.getLevel().getServer().getPlayerList().remove(player);
    }
  }

  @GameTest(maxTicks = 400)
  public void deathRespawnAndCrossDimensionFlashback(GameTestHelper helper) throws Exception {
    var server = helper.getLevel().getServer();
    var player = StormServerGameTest.player(helper.getLevel(), "death-respawn");
    try {
      var nether = server.getLevel(Level.NETHER);
      Vec3 deathPos = new Vec3(12.5, 80, -24.5);
      player.setRespawnPosition(new ServerPlayer.RespawnConfig(
          LevelData.RespawnData.of(Level.OVERWORLD, helper.absolutePos(BlockPos.ZERO).above(3), 0, 0), true), false);
      var data = skills(player);
      data.setSkillEnabled(SkillData.SKILL_DEATH_FLASHBACK, true);
      publish(player, data);
      helper.assertTrue(player.teleportTo(nether, deathPos.x, deathPos.y, deathPos.z, Set.of(), 0, 0, false),
          "death fixture enters nether");
      // Complete the client load handshake before testing lethal damage.
      player.hasChangedDimension();
      player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
      var beforeDeath = skills(player);
      player.hurtServer(nether, player.damageSources().genericKill(), Float.MAX_VALUE);
      helper.assertTrue(!beforeDeath.hasDeathLocation(),
          "death recording leaves an earlier queued snapshot unchanged");
      helper.assertTrue(player.isDeadOrDying() && deathPos.equals(skills(player).getLastDeathLocation()),
          "actual death records its location");
      player.connection.handleClientCommand(new ServerboundClientCommandPacket(
          ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
      player = player.connection.player;
      player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
      helper.assertTrue(player.level() == server.overworld() && skills(player).hasDeathLocation()
              && "minecraft:the_nether".equals(skills(player).getLastDeathDimension()),
          "death attachment survives real respawn in another dimension");
      helper.assertTrue(flashback(player) == 1, "flashback command succeeds after respawn");
      helper.assertTrue(player.level() == nether
              && player.position().distanceToSqr(deathPos) < 1.0e-8 && !skills(player).hasDeathLocation(),
          "successful cross-dimension flashback consumes the respawned player's record");
      helper.assertTrue(flashback(player) == 0, "cross-dimension record cannot be reused");
      helper.succeed();
    } finally {
      server.getPlayerList().remove(player);
    }
  }
}
