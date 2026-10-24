package top.bearcabbage.modpackupdater;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;

public final class ModpackUpdaterConfig {
    public String serverUrl = "";
    public String trustedPublicKey = "";
    /** Legacy field retained for configuration compatibility; every startup now checks. */
    public boolean checkOnStartup = true;
    /** Read for migration only. Old unsigned cloud manifests are never trusted. */
    public String updateUrl;

    public static ModpackUpdaterConfig load() throws IOException {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("modpackupdater.json");
        var gson = new GsonBuilder().setPrettyPrinting().create();
        if (!Files.exists(path)) {
            var config = new ModpackUpdaterConfig();
            Files.createDirectories(path.getParent());
            Files.writeString(path, gson.toJson(config) + "\n", StandardOpenOption.CREATE_NEW);
            return config;
        }
        try {
            if (Files.size(path) > 65536) throw new IOException("更新器配置过大");
            var config = gson.fromJson(Files.readString(path), ModpackUpdaterConfig.class);
            if (config == null) throw new IOException("更新器配置为空");
            if ((config.serverUrl == null || config.serverUrl.isBlank()) && config.updateUrl != null
                    && config.updateUrl.endsWith("/v1/manifest.json"))
                config.serverUrl = config.updateUrl.substring(0, config.updateUrl.length() - "v1/manifest.json".length());
            return config;
        } catch (RuntimeException e) { throw new IOException("config/modpackupdater.json 格式无效", e); }
    }
}
