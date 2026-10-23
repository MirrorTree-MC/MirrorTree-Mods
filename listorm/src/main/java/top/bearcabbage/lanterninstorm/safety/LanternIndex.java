package top.bearcabbage.lanterninstorm.safety;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import top.bearcabbage.lanterninstorm.content.SpiritLanternBlock;

/** Indexes lamp positions once per loaded chunk; queries never load or generate chunks. */
public final class LanternIndex {
  private static final Map<ServerLevel, Map<Long, Set<BlockPos>>> WORLDS = new IdentityHashMap<>();

  public static void add(ServerLevel level, BlockPos pos) {
    WORLDS
        .computeIfAbsent(level, k -> new HashMap<>())
        .computeIfAbsent(ChunkPos.pack(pos), k -> new HashSet<>())
        .add(pos.immutable());
  }

  public static void load(ServerLevel level, LevelChunk chunk) {
    unload(level, chunk);
    chunk.findBlocks(
        s -> s.getBlock() instanceof SpiritLanternBlock, (pos, state) -> add(level, pos));
  }

  public static void unload(ServerLevel level, LevelChunk chunk) {
    var chunks = WORLDS.get(level);
    if (chunks != null) chunks.remove(chunk.getPos().pack());
  }

  public static boolean isSafe(ServerLevel level, Vec3 player) {
    var chunks = WORLDS.get(level);
    if (chunks == null) return false;
    BlockPos center = BlockPos.containing(player);
    int cx = center.getX() >> 4, cz = center.getZ() >> 4;
    for (int x = cx - 1; x <= cx + 1; x++)
      for (int z = cz - 1; z <= cz + 1; z++) {
        if (!level.hasChunk(x, z)) continue;
        var positions = chunks.get(ChunkPos.pack(x, z));
        if (positions == null) continue;
        var it = positions.iterator();
        while (it.hasNext()) {
          BlockPos pos = it.next();
          var state = level.getBlockState(pos);
          if (!(state.getBlock() instanceof SpiritLanternBlock lamp)) {
            it.remove();
            continue;
          }
          if (!state.getValue(SpiritLanternBlock.STARTUP)) continue;
          int radius =
              level.dimension().identifier().getNamespace().equals("minecraft") ? 8 : lamp.radius();
          if (within(player, Vec3.atCenterOf(pos), radius)) return true;
        }
      }
    return false;
  }

  public static boolean within(Vec3 a, Vec3 b, double radius) {
    return Math.abs(a.x() - b.x()) <= radius
        && Math.abs(a.y() - b.y()) <= radius
        && Math.abs(a.z() - b.z()) <= radius;
  }

  public static void clear() {
    WORLDS.clear();
  }
}
