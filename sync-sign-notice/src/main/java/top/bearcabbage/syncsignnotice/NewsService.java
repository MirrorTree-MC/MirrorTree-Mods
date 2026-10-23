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
        return CompletableFuture.runAsync(() -> {
            pullNewsSync(dbConfig, noticeCache, maxNewsCount);
        });
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
            int totalCount = getTotalNewsCount(connection);
            
            SyncSignNotice.LOGGER.info("[SyncSignNotice] Database has {} total news items, requesting {} items", totalCount, maxNewsCount);
            
            if (totalCount == 0) {
                handleEmptyDatabase(noticeCache);
                return;
            }
            
            int actualLimit = Math.min(maxNewsCount, totalCount);
            if (actualLimit < maxNewsCount) {
                SyncSignNotice.LOGGER.info("[SyncSignNotice] Adjusted request limit from {} to {} (database only has {} items)", 
                           maxNewsCount, actualLimit, totalCount);
            }
            
            List<NewsItem> newsItems = fetchNewsItems(connection, actualLimit);
            
            SyncSignNotice.publishNews(newsItems.stream().map(NewsService::createSignText).toList());

            // 缓存新闻到本地
            cacheNewsItems(noticeCache, newsItems);
            
            logPullResult(newsItems.size(), maxNewsCount, totalCount);
            
        } catch (SQLException e) {
            SyncSignNotice.LOGGER.error("[SyncSignNotice] Failed to connect to database: {}", e.getMessage());
            loadFromCache(noticeCache);
        }
    }
    
    /**
     * 获取数据库中新闻总数
     */
    private static int getTotalNewsCount(Connection connection) throws SQLException {
        String countQuery = "SELECT COUNT(*) FROM jellynews";
        try (PreparedStatement countStmt = connection.prepareStatement(countQuery);
             ResultSet countRs = countStmt.executeQuery()) {
            if (countRs.next()) {
                return countRs.getInt(1);
            }
        }
        return 0;
    }
    
    /**
     * 处理空数据库情况
     */
    private static void handleEmptyDatabase(SConfig noticeCache) {
        SyncSignNotice.LOGGER.warn("[SyncSignNotice] No news items found in database");
        SyncSignNotice.publishNews(List.of());
        noticeCache.set("lastUpdate", System.currentTimeMillis());
        noticeCache.set("newsItems", new ArrayList<NewsItem>());
        noticeCache.save();
    }
    
    /**
     * 从数据库获取新闻项
     */
    private static List<NewsItem> fetchNewsItems(Connection connection, int limit) throws SQLException {
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
        
        return newsItems;
    }
    
    /**
     * 缓存新闻项到本地
     */
    private static void cacheNewsItems(SConfig noticeCache, List<NewsItem> newsItems) {
        noticeCache.set("lastUpdate", System.currentTimeMillis());
        noticeCache.set("newsItems", newsItems);
        noticeCache.save();
    }
    
    /**
     * 记录拉取结果日志
     */
    private static void logPullResult(int actualCount, int requestedCount, int totalCount) {
        if (actualCount < requestedCount) {
            SyncSignNotice.LOGGER.info("[SyncSignNotice] Successfully pulled {} news items (requested {}, but database only has {})", 
                       actualCount, requestedCount, totalCount);
        } else {
            SyncSignNotice.LOGGER.info("[SyncSignNotice] Successfully pulled {} news items", actualCount);
        }
    }
    
    /**
     * 创建SignText对象
     */
    private static SignText createSignText(NewsItem item) {
        String part1 = !item.content.isEmpty() ? item.content.substring(0, Math.min(10, item.content.length())) : "";
        String part2 = item.content.length() > 10 ? item.content.substring(10, Math.min(20, item.content.length())) : "";
        String part3 = item.content.length() > 20 ? item.content.substring(20, Math.min(30, item.content.length())) : "";

        return new SignText(
            new net.minecraft.network.chat.Component[]{
                Component.literal("[" + item.type + "] " + item.name), 
                Component.literal(part1), 
                Component.literal(part2), 
                Component.literal(part3)
            },
            new net.minecraft.network.chat.Component[]{
                    Component.literal("[" + item.type + "] " + item.name),
                    Component.literal(part1),
                    Component.literal(part2),
                    Component.literal(part3)
            },
            DyeColor.BLACK,
            true
        );
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
