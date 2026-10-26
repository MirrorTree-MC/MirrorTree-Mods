package top.bearcabbage.syncsignnotice;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

/**
 * 新闻服务类
 */
@Environment(EnvType.CLIENT)
public class NewsService {
    
    /**
     * 从远程数据库拉取新闻（异步执行）
     */
    public static CompletableFuture<Void> pullNews(DatabaseConfig dbConfig, SConfig noticeCache, int maxNewsCount) {
        return CompletableFuture.runAsync(() -> pullNewsSync(dbConfig, noticeCache, maxNewsCount));
    }
    
    /**
     * 从远程数据库拉取新闻（同步方法）
     */
    private static void pullNewsSync(DatabaseConfig dbConfig, SConfig noticeCache, int maxNewsCount) {
        if (dbConfig == null) {
            SyncSignNotice.LOGGER.error("[SyncSignNotice] Database config not loaded!");
            return;
        }
        
        // 尝试加载MySQL驱动
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            SyncSignNotice.LOGGER.info("[SyncSignNotice] MySQL JDBC driver loaded successfully");
        } catch (ClassNotFoundException e) {
            SyncSignNotice.LOGGER.error("[SyncSignNotice] MySQL JDBC driver not found: {}", e.getMessage());
            loadFromCache(noticeCache);
            return;
        }
        
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&connectTimeout=10000&socketTimeout=10000", 
                                  dbConfig.host, dbConfig.port, dbConfig.database);
        
        try (Connection connection = DriverManager.getConnection(url, dbConfig.user, dbConfig.password)) {
            readAndPublishNews(connection, noticeCache, maxNewsCount);
        } catch (SQLException e) {
            SyncSignNotice.LOGGER.error("[SyncSignNotice] Failed to connect to database: {}", e.getMessage());
            loadFromCache(noticeCache);
        }
    }
    
    /** Read one bounded result and publish/cache the same complete snapshot, including empty results. */
    static void readAndPublishNews(Connection connection, SConfig noticeCache, int limit) throws SQLException {
        String query = "SELECT created_at, type, name, content FROM jellynews ORDER BY created_at DESC LIMIT ?";
        List<NewsItem> newsItems = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    NewsItem item = new NewsItem();
                    item.createdAt = rs.getTimestamp("created_at");
                    item.type = rs.getString("type");
                    item.name = rs.getString("name");
                    item.content = rs.getString("content");
                    newsItems.add(item);
                }
            }
        }
        SyncSignNotice.publishNews(newsItems.stream().map(NewsService::createSignText).toList());
        noticeCache.set("lastUpdate", System.currentTimeMillis());
        noticeCache.set("newsItems", newsItems);
        noticeCache.save();
        SyncSignNotice.LOGGER.info("[SyncSignNotice] Successfully pulled {} news items (requested {})", newsItems.size(), limit);
    }

    /**
     * 创建SignText对象
     */
    private static SignText createSignText(NewsItem item) {
        int[] content = item.content == null ? new int[0] : item.content.codePoints().limit(30).toArray();
        String[] lines = {"[" + item.type + "] " + item.name, "", "", ""};
        for (int line = 1, offset = 0; line < lines.length && offset < content.length; line++, offset += 10) {
            lines[line] = new String(content, offset, Math.min(10, content.length - offset));
        }
        Component[] messages = new Component[4], filteredMessages = new Component[4];
        for (int line = 0; line < lines.length; line++) {
            messages[line] = Component.literal(lines[line]);
            filteredMessages[line] = Component.literal(lines[line]);
        }
        return new SignText(messages, filteredMessages, DyeColor.BLACK, true);
    }
    
    /**
     * 从缓存加载新闻
     */
    private static void loadFromCache(SConfig noticeCache) {
        try {
            Long lastUpdate = noticeCache.get("lastUpdate", Long.class);
            if (lastUpdate != null && System.currentTimeMillis() - lastUpdate < 24 * 60 * 60 * 1000) { // 24小时内的缓存
                SyncSignNotice.LOGGER.info("[SyncSignNotice] Loading news from cache");
                
                // 从缓存加载新闻数据
                NewsItem[] cachedItems = noticeCache.get("newsItems", NewsItem[].class);
                SyncSignNotice.publishNews(cachedItems == null ? List.of()
                        : java.util.Arrays.stream(cachedItems).map(NewsService::createSignText).toList());
            } else {
                SyncSignNotice.publishNews(List.of());
                SyncSignNotice.LOGGER.warn("[SyncSignNotice] Cache is too old or not found");
            }
        } catch (Exception e) {
            SyncSignNotice.LOGGER.error("[SyncSignNotice] Failed to load from cache: {}", e.getMessage());
        }
    }
}
