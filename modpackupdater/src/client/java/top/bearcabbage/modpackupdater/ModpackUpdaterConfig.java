package top.bearcabbage.modpackupdater;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

public class ModpackUpdaterConfig {
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("modpackupdater.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String updateUrl = "https://gitee.com/integrity_k/.github-private/releases/download/astreet/modpack_update_manifest.json";

    public static ModpackUpdaterConfig load() {
        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                return GSON.fromJson(reader, ModpackUpdaterConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        ModpackUpdaterConfig config = new ModpackUpdaterConfig();
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
