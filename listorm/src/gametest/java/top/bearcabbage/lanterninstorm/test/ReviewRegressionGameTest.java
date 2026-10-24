package top.bearcabbage.lanterninstorm.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;
import top.bearcabbage.lanterninstorm.LanternInStormAPI;
import top.bearcabbage.lanterninstorm.progression.PlayerStore;

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
}
