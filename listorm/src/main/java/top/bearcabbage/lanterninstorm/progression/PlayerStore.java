package top.bearcabbage.lanterninstorm.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import top.bearcabbage.lanterninstorm.LanternInStorm;

/**
 * One world-scoped store. Saves only dirty records, atomically, rather than rewriting on every
 * biome tick.
 */
public final class PlayerStore {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final Map<UUID, State> PLAYERS = new HashMap<>();

  public static final class State {
    public Map<String, Map<String, Integer>> counts = new HashMap<>();
    public int[] spawn;
    public String spawnDimension = "minecraft:overworld";
    public transient boolean dirty;
    public transient int graceTicks = 200;
    public transient int tick;
    public transient boolean safe = true;
    public transient boolean cleared;

    public int increment(String type, String key) {
      dirty = true;
      return counts
          .computeIfAbsent(type, k -> new HashMap<>())
          .merge(key, 1, (a, b) -> a == Integer.MAX_VALUE ? a : a + b);
    }

    public int count(String type, String key) {
      return counts.getOrDefault(type, Map.of()).getOrDefault(key, 0);
    }
  }

  private static Path path(ServerPlayer player) {
    return player
        .level()
        .getServer()
        .getWorldPath(LevelResource.ROOT)
        .resolve("lantern-in-storm/players")
        .resolve(player.getUUID() + ".json");
  }

  public static State get(ServerPlayer player) {
    return PLAYERS.computeIfAbsent(
        player.getUUID(),
        id -> {
          Path path = path(player);
          if (Files.isRegularFile(path))
            try (Reader reader = Files.newBufferedReader(path)) {
              State state = GSON.fromJson(reader, State.class);
              if (state != null && state.counts != null) {
                state.graceTicks = 200;
                state.safe = true;
                return state;
              }
            } catch (IOException | RuntimeException e) {
              throw new IllegalStateException("Cannot read player progress " + path, e);
            }
          return new State();
        });
  }

  public static void save(ServerPlayer player) {
    State state = PLAYERS.get(player.getUUID());
    if (state == null || !state.dirty) return;
    Path path = path(player), temp = path.resolveSibling(path.getFileName() + ".tmp");
    try {
      Files.createDirectories(path.getParent());
      try (Writer writer = Files.newBufferedWriter(temp)) {
        GSON.toJson(state, writer);
      }
      try {
        Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
      }
      state.dirty = false;
    } catch (IOException e) {
      LanternInStorm.LOGGER.error("Cannot save player progress {}", path, e);
    }
  }

  public static void leave(ServerPlayer player) {
    save(player);
    if (!get(player).dirty) PLAYERS.remove(player.getUUID());
  }

  public static void clear() {
    PLAYERS.clear();
  }
}
