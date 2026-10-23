package top.bearcabbage.syncsignnotice;

import java.nio.file.Path;
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
                var cache=new SConfig(Path.of("notice-regression-cache.json").toAbsolutePath());
                var empty=NewsService.class.getDeclaredMethod("handleEmptyDatabase",SConfig.class);
                empty.setAccessible(true);
                SyncSignNotice.publishNews(List.of(news));
                empty.invoke(null,cache);
                check(sign.getFrontText()==marker,"empty database clears published notices");
                var item=new NewsItem();item.type="测试";item.name="作者";item.content="离线缓存测试";
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
}
