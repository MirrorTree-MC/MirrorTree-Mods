package top.bearcabbage.syncsignnotice;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.entity.SignText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncSignNotice implements ClientModInitializer {
    public static final String MOD_ID = "sync-sign-notice";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final SConfig config = new SConfig(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("config.json"));
    private static final SConfig noticeCache = new SConfig(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("notice_cache.json"), true);
    public static int maxNewsCount = 5;
    private static int refreshInterval = 6000, clientTick;
    private static volatile List<SignText> newsList = List.of();
    private static final AtomicBoolean pulling = new AtomicBoolean();
    private static DatabaseConfig dbConfig;

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            loadConfig();
            pullNews();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++clientTick >= refreshInterval) {
                clientTick = 0;
                pullNews();
            }
        });
    }

    public static void loadConfig() {
        dbConfig = new DatabaseConfig(config.getOrDefault("db_host", "example.com"),
                config.getOrDefault("db_user", "ExampleUser"),
                config.getOrDefault("db_password", "ExamplePassword"),
                config.getOrDefault("db_database", "example_db"), config.getOrDefault("db_port", 3306));
        maxNewsCount = Math.max(1, config.getOrDefault("maxNewsCount", 5));
        refreshInterval = (int) Math.min(Integer.MAX_VALUE,
                20L * Math.max(1, config.getOrDefault("autoRefreshInterval", 300)));
    }

    public static void pullNews() {
        if (!pulling.compareAndSet(false, true)) return;
        NewsService.pullNews(dbConfig, noticeCache, maxNewsCount).whenComplete((unused, error) -> {
            pulling.set(false);
            if (error != null) LOGGER.error("Notice refresh failed", error);
        });
    }

    static void publishNews(List<SignText> signs) {
        newsList = List.copyOf(signs);
    }

    public static SignText noticeFor(SignText original) {
        int number = getNewsSignNumber(original);
        List<SignText> snapshot = newsList;
        return number > 0 && number <= maxNewsCount && number <= snapshot.size()
                ? snapshot.get(number - 1) : original;
    }

    public static int getNewsSignNumber(SignText text) {
        if (text == null) return 0;
        var messages = text.getMessages(false);
        if (!messages[0].getString().equals("[Notice]") || !messages[3].getString().equals("SyncSign")) return 0;
        try {
            return Integer.parseInt(messages[1].getString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
