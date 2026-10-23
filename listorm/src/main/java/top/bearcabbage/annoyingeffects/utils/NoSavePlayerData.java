package top.bearcabbage.annoyingeffects.utils;

import java.util.*;
import net.minecraft.world.entity.player.Player;

public final class NoSavePlayerData<T> {
  public final T initialValue;
  private final Map<UUID, T> values = new HashMap<>();

  public NoSavePlayerData(T initialValue) {
    this.initialValue = initialValue;
  }

  public T get(UUID id) {
    return values.getOrDefault(id, initialValue);
  }

  public T get(Player player) {
    return get(player.getUUID());
  }

  public void set(UUID id, T value) {
    if (Objects.equals(initialValue, value)) values.remove(id);
    else values.put(id, value);
  }

  public void set(Player player, T value) {
    set(player.getUUID(), value);
  }

  public void reset(UUID id) {
    values.remove(id);
  }

  public void reset(Player player) {
    reset(player.getUUID());
  }

  public void clear() {
    values.clear();
  }

  public void cleanTrash() {
    values.values().removeIf(v -> Objects.equals(v, initialValue));
  }
}
