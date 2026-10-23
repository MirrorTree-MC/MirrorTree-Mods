package top.bearcabbage.annoyingeffects.utils;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Retains custom parameters and fills missing fields without overwriting the user's values. */
public final class ConfigReadAndWrite {
  private final Path path;
  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private JsonObject data = new JsonObject();

  public ConfigReadAndWrite(Path path) {
    this.path = path;
    load();
  }

  public void load() {
    if (!Files.exists(path)) return;
    try (Reader reader = Files.newBufferedReader(path)) {
      data = JsonParser.parseReader(reader).getAsJsonObject();
    } catch (IOException | RuntimeException e) {
      throw new IllegalStateException("Cannot read effect configuration " + path, e);
    }
  }

  public void save() {
    try {
      Files.createDirectories(path.getParent());
      Path temp = path.resolveSibling(path.getFileName() + ".tmp");
      try (Writer writer = Files.newBufferedWriter(temp)) {
        gson.toJson(data, writer);
      }
      try {
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot save effect configuration " + path, e);
    }
  }

  public Map<String, Integer> getStatusParameters(String key, Map<String, Integer> defaults) {
    JsonObject values = data.has(key) ? data.getAsJsonObject(key) : new JsonObject();
    Map<String, Integer> result = new LinkedHashMap<>();
    for (var entry : defaults.entrySet()) {
      int value =
          values.has(entry.getKey()) ? values.get(entry.getKey()).getAsInt() : entry.getValue();
      result.put(entry.getKey(), value);
      values.addProperty(entry.getKey(), value);
    }
    for (String prefix : List.of("", "weak_"))
      if (result.get(prefix + "duration") >= 0 && result.get(prefix + "interval") <= 0)
        throw new IllegalArgumentException(
            "Effect " + key + " requires a positive " + prefix + "interval when enabled");
    data.add(key, values);
    return Map.copyOf(result);
  }
}
