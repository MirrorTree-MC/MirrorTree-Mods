package top.bearcabbage.syncsignnotice;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;

public final class NoticeGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
        System.out.println("NOTICE_CHECK: "+message);
    }
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
        context.runOnClient(client -> {
            var marker=new SignText().setMessage(0,Component.literal("[Notice]"))
                    .setMessage(1,Component.literal("1")).setMessage(3,Component.literal("SyncSign"));
            var news=new SignText().setMessage(0,Component.literal("迁移测试公告"));
            var sign=new SignBlockEntity(BlockPos.ZERO,Blocks.OAK_SIGN.defaultBlockState());
            sign.setLevel(client.level);
            sign.setText(marker,true);
            SyncSignNotice.publishNews(List.of(news));
            check(sign.getFrontText()==news,"actual client sign mixin replaces notice marker");
            check(sign.getBackText().getMessage(0,false).getString().isEmpty(),"back text remains unchanged");
            SyncSignNotice.publishNews(List.of());
            check(sign.getFrontText()==marker,"empty news restores original marker without altering sign data");
            check(SyncSignNotice.noticeFor(new SignText())!=news,"ordinary signs remain unchanged");
            var writer=new Thread(() -> {
                for(int i=0;i<100000;i++)SyncSignNotice.publishNews(i%2==0?List.of():List.of(news));
            });
            writer.start();
            for(int i=0;i<100000;i++){
                var text=sign.getFrontText();
                if(text!=marker&&text!=news)throw new AssertionError("inconsistent notice snapshot");
            }
            try {writer.join();}catch(InterruptedException e){throw new RuntimeException(e);}
            check(true,"concurrent publication and rendering have no partial list or index errors");
            try {
                Path damaged = Path.of("notice-damaged-cache.json").toAbsolutePath();
                for (String json : List.of("", "{\"lastUpdate\":123,\"newsItems\":[", "[]", "null")) {
                    Files.writeString(damaged, json);
                    var recovered = new SConfig(damaged, true);
                    check(recovered.get("lastUpdate", Long.class)==null, "invalid cache is discarded: " + json.length());
                    recovered.set("lastUpdate", 123L); recovered.save();
                    check(new SConfig(damaged).get("lastUpdate", Long.class)==123L, "recovered cache can be saved and reopened");
                }
                Files.writeString(damaged, "{");
                boolean rejected = false;
                try { new SConfig(damaged); } catch (com.google.gson.JsonParseException | IllegalStateException expected) { rejected = true; }
                check(rejected && Files.readString(damaged).equals("{"), "invalid user configuration is preserved");
                Files.delete(damaged);
                var cache=new SConfig(Path.of("notice-regression-cache.json").toAbsolutePath());
                var item=new NewsItem();item.type="测试";item.name="作者";item.content="离线缓存测试";
                int[] query = new int[2];
                NewsService.readAndPublishNews(newsConnection(List.of(item), query), cache, 5);
                check(query[0] == 1 && query[1] == 5,
                        "refresh executes one LIMIT query with the configured bound");
                check(sign.getFrontText().getMessage(0,false).getString().equals("[测试] 作者")
                        && cache.get("newsItems", NewsItem[].class).length == 1,
                        "JDBC result publishes and caches the same notices");
                NewsService.readAndPublishNews(newsConnection(List.of(), new int[2]), cache, 5);
                check(sign.getFrontText()==marker,"empty JDBC result clears published notices");
                var savedCache = new SConfig(Path.of("notice-regression-cache.json").toAbsolutePath());
                check(savedCache.get("newsItems", NewsItem[].class).length == 0
                        && savedCache.get("lastUpdate", Long.class) > 0,
                        "empty JDBC result persists an empty fresh cache");
                cache.set("lastUpdate",System.currentTimeMillis());cache.set("newsItems",new NewsItem[]{item});cache.save();
                var load=NewsService.class.getDeclaredMethod("loadFromCache",SConfig.class);load.setAccessible(true);
                load.invoke(null,cache);
                check(sign.getFrontText().getMessage(0,false).getString().equals("[测试] 作者"),"fresh offline cache restores formatted notices");
                cache.set("lastUpdate",0L);load.invoke(null,cache);
                check(sign.getFrontText()==marker,"expired cache does not retain stale notices");
                Class.forName("com.mysql.cj.jdbc.Driver");
                check(true,"packaged JDBC dependency loads");
            }catch(Exception e){throw new RuntimeException(e);}
        });
        }
    }

    private static Connection newsConnection(List<NewsItem> rows, int[] query) {
        int[] index = {-1};
        ResultSet results = jdbc(ResultSet.class, (proxy, method, args) -> switch (method.getName()) {
            case "next" -> ++index[0] < rows.size();
            case "getTimestamp" -> rows.get(index[0]).createdAt;
            case "getString" -> switch ((String) args[0]) {
                case "type" -> rows.get(index[0]).type;
                case "name" -> rows.get(index[0]).name;
                case "content" -> rows.get(index[0]).content;
                default -> throw new AssertionError("Unexpected column: " + args[0]);
            };
            case "close" -> null;
            default -> throw new AssertionError("Unexpected ResultSet call: " + method.getName());
        });
        PreparedStatement statement = jdbc(PreparedStatement.class, (proxy, method, args) -> switch (method.getName()) {
            case "setInt" -> {
                if ((int) args[0] != 1) throw new AssertionError("Unexpected LIMIT parameter");
                query[1] = (int) args[1];
                yield null;
            }
            case "executeQuery" -> { query[0]++; yield results; }
            case "close" -> null;
            default -> throw new AssertionError("Unexpected statement call: " + method.getName());
        });
        return jdbc(Connection.class, (proxy, method, args) -> {
            if (!method.getName().equals("prepareStatement")
                    || !args[0].equals("SELECT created_at, type, name, content FROM jellynews ORDER BY created_at DESC LIMIT ?"))
                throw new AssertionError("Unexpected database request: " + method.getName());
            return statement;
        });
    }

    private static <T> T jdbc(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
