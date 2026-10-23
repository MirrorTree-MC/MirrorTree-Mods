package top.bearcabbage.lanterninstorm;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import top.bearcabbage.lanterninstorm.content.BeginningLanternEntity;
import top.bearcabbage.lanterninstorm.progression.PlayerStore;

/** Public integration surface for server scripts and other 26.1.2 mods. */
public final class LanternInStormAPI {
  public static boolean setRTPSpawnWhenSpawnpointCommand = true;
  public static final Set<ResourceKey<Level>> safeWorlds = new HashSet<>();

  public static void overrideRTPSpawnSetting() {
    setRTPSpawnWhenSpawnpointCommand = false;
  }

  public static void addSafeWorld(ResourceKey<Level> world) {
    safeWorlds.add(world);
  }

  public static void setRTPSpawn(ServerPlayer player, BlockPos pos, boolean warp) {
    var state = PlayerStore.get(player);
    state.spawn = new int[] {pos.getX(), pos.getY(), pos.getZ()};
    state.spawnDimension = player.level().dimension().identifier().toString();
    state.graceTicks = 1600;
    state.dirty = true;
    PlayerStore.save(player);
    if (!setRTPSpawnWhenSpawnpointCommand)
      player.setRespawnPosition(
          new ServerPlayer.RespawnConfig(
              LevelData.RespawnData.of(player.level().dimension(), pos, 0, 0), true),
          false);
    if (!warp) {
      BeginningLanternEntity.create(
          player.level(), pos, "入梦点[" + player.getName().getString() + "]", false);
      player.connection.send(
          new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
              net.minecraft.network.chat.Component.literal("大鹏的梦").withColor(0x525288)));
      player.connection.send(
          new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
              net.minecraft.network.chat.Component.literal("这个「灯笼」是你的起点")));
    }
  }

  public static BlockPos getRTPSpawn(ServerPlayer player) {
    var p = PlayerStore.get(player).spawn;
    return p == null ? null : new BlockPos(p[0], p[1], p[2]);
  }

  public static void setWarpSpawn(ServerLevel level, BlockPos pos, String name) {
    BeginningLanternEntity.create(level, pos, "聚落入梦点[" + name + "]", true);
  }
}
