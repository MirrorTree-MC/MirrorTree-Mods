package top.bearcabbage.lanterninstorm.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;
import top.bearcabbage.lanterninstorm.content.*;
import top.bearcabbage.lanterninstorm.progression.*;
import top.bearcabbage.lanterninstorm.safety.*;

/** Runs in Fabric's headless game-test server, with two independent network player records. */
public class StormServerGameTest {
  static ServerPlayer player(ServerLevel level, String name) {
    return player(level, new GameProfile(UUID.randomUUID(), name));
  }

  static ServerPlayer player(ServerLevel level, GameProfile profile) {
    var cookie = CommonListenerCookie.createInitial(profile, false);
    var player =
        new ServerPlayer(
            level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
    var connection = new Connection(PacketFlow.SERVERBOUND);
    new EmbeddedChannel(connection);
    level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
    player.setGameMode(GameType.SURVIVAL);
    return player;
  }

  @GameTest(maxTicks = 200)
  public void multiplayerIsolation(GameTestHelper helper) {
    var level = helper.getLevel();
    var first = player(level, "storm-a");
    var second = player(level, "storm-b");
    try {
      var pos = helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2));
      level.setBlockAndUpdate(
          pos,
          StormContent.LAMPS
              .get("oak_wooden_lantern")
              .defaultBlockState()
              .setValue(SpiritLanternBlock.STARTUP, true));
      first.teleportTo(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
      second.teleportTo(pos.getX() + 48.5, pos.getY() + .5, pos.getZ() + .5);
      var a = PlayerStore.get(first);
      var b = PlayerStore.get(second);
      a.graceTicks = b.graceTicks = 0;
      a.tick = b.tick = 19;
      SafetyService.tick(first);
      SafetyService.tick(second);
      helper.assertTrue(a.safe && !b.safe, "players have independent safe regions");
      helper.assertTrue(
          !first.hasEffect(AnnoyingEffects.TANGLING_DREAMS)
              && second.hasEffect(AnnoyingEffects.TANGLING_DREAMS),
          "only unsafe player gets dreams");
      ProgressionService.award(first, "food", "test:shared", 1);
      ProgressionService.award(second, "food", "test:shared", 1);
      ProgressionService.award(second, "food", "test:shared", 1);
      helper.assertTrue(
          a.count("food", "test:shared") == 1 && b.count("food", "test:shared") == 2,
          "player progression counters are isolated");
      double blockReach = first.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
      double entityReach = first.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
      first.addEffect(new MobEffectInstance(AnnoyingEffects.CONTROLS_SHORT_REACH, 40));
      helper.assertTrue(
          Math.abs(first.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) - (blockReach - 1))
              < 1.0e-8,
          "short reach still reduces block interaction distance");
      helper.assertTrue(
          Math.abs(first.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - (entityReach - 1))
              < 1.0e-8,
          "short reach still reduces entity interaction distance");
      double safeFallDistance = first.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
      double fallDamageMultiplier = first.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
      double gravity = first.getAttributeValue(Attributes.GRAVITY);
      first.addEffect(new MobEffectInstance(AnnoyingEffects.HEAVINESS, 40));
      helper.assertTrue(
          Math.abs(first.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) - (safeFallDistance - 2.25))
              < 1.0e-8,
          "heaviness still reduces safe fall distance");
      helper.assertTrue(
          Math.abs(first.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)
                  - (fallDamageMultiplier + 1))
              < 1.0e-8,
          "heaviness still increases fall damage");
      helper.assertTrue(
          Math.abs(first.getAttributeValue(Attributes.GRAVITY) - gravity * 2) < 1.0e-8,
          "heaviness still doubles gravity");
      first.setDeltaMovement(Vec3.ZERO);
      first.baseTick();
      helper.assertTrue(
          first.getDeltaMovement().y() < -.14, "heavy effect applies extra gravity on server");
      PlayerStore.save(first);
      PlayerStore.save(second);
      helper.succeed();
    } finally {
      level.getServer().getPlayerList().remove(first);
      level.getServer().getPlayerList().remove(second);
    }
  }
}
