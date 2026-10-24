package top.bearcabbage.syncsignnotice;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * 配置文件管理类
 */
public class SConfig {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SConfig.class);
    private final Path filePath;
    private JsonObject jsonObject;
    private final Gson gson;

    public SConfig(Path filePath) {
        this(filePath, false);
    }

    public SConfig(Path filePath, boolean recoverInvalidCache) {
        this.filePath = filePath;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            Files.createDirectories(filePath.getParent());
            if (Files.notExists(filePath)) {
                jsonObject = new JsonObject();
                save();
                return;
            }
        } catch (IOException e) {
            LOGGER.error(e.toString());
        }
        loadConfig(recoverInvalidCache);
    }

    private void loadConfig(boolean recoverInvalidCache) {
        try (var reader = Files.newBufferedReader(filePath)) {
            this.jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            if (!recoverInvalidCache) throw e;
            LOGGER.warn("Ignoring invalid notice cache {}", filePath, e);
            this.jsonObject = new JsonObject();
        } catch (IOException e) {
            this.jsonObject = new JsonObject();
        }
    }

    public void save() {
        Path temp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temp)) {
                gson.toJson(jsonObject, writer);
            }
            try {
                Files.move(temp, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error(e.toString());
        }
    }

    public void set(String key, Object value) {
        jsonObject.add(key, gson.toJsonTree(value));
    }

    public <T> T get(String key, Class<T> clazz) {
        return gson.fromJson(jsonObject.get(key), clazz);
    }

    public <T> T getOrDefault(String key, T defaultValue) {
        if (jsonObject.has(key)) {
            @SuppressWarnings("unchecked")
            Class<T> clazz = (Class<T>) defaultValue.getClass();
            return gson.fromJson(jsonObject.get(key), clazz);
        }
        else {
            set(key, defaultValue);
            save();
            return defaultValue;
        }
    }
}
