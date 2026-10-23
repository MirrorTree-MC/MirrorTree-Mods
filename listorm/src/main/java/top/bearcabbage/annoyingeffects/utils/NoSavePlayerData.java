package top.bearcabbage.annoyingeffects.utils;

import java.util.*;
import net.minecraft.world.entity.player.Player;

public final class NoSavePlayerData<T> {
  private final T initialValue;
  private final Map<UUID, T> values = new HashMap<>();

  public NoSavePlayerData(T initialValue) {
    this.initialValue = initialValue;
  }

  public T get(Player player) {
    return values.getOrDefault(player.getUUID(), initialValue);
  }

  public void set(Player player, T value) {
    if (Objects.equals(initialValue, value)) values.remove(player.getUUID());
    else values.put(player.getUUID(), value);
  }

  public void reset(Player player) {
    values.remove(player.getUUID());
  }

  public void clear() {
    values.clear();
  }
}
