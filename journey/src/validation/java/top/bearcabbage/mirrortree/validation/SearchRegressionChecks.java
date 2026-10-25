package top.bearcabbage.mirrortree.validation;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import top.bearcabbage.mirrortree.dream.MTDream;

/** Destructive search checks, called only from the isolated validation probe. */
final class SearchRegressionChecks {
    @SuppressWarnings("unchecked")
    static void run(MinecraftServer server, ServerPlayer player, BlockPos bed,
            BiConsumer<Boolean, String> check) throws Exception {
        var world = server.overworld();
        var bedroom = player.level();
        var originalPosition = player.position();
        var border = world.getWorldBorder();
        double centerX = border.getCenterX(), centerZ = border.getCenterZ(), size = border.getSize();
        var sample = MTDream.class.getDeclaredMethod("randomCandidate", ServerLevel.class); sample.setAccessible(true);
        var tick = MTDream.class.getDeclaredMethod("tick", MinecraftServer.class); tick.setAccessible(true);
        var release = MTDream.class.getDeclaredMethod("releaseSearch", ServerLevel.class); release.setAccessible(true);
        var queueField = MTDream.class.getDeclaredField("SEARCHES"); queueField.setAccessible(true);
        var searches = (LinkedHashMap<UUID, Object>) queueField.get(null);
        var pending = MTDream.class.getDeclaredField("pendingSearch"); pending.setAccessible(true);
        var constructor = pending.getType().getDeclaredConstructors()[0]; constructor.setAccessible(true);
        var timeout = MTDream.class.getDeclaredField("CHUNK_WAIT_NANOS"); timeout.setAccessible(true);
        var ticket = MTDream.class.getDeclaredField("SEARCH_TICKET"); ticket.setAccessible(true);
        TicketType searchTicket=(TicketType)ticket.get(null);
        var storageField = ServerChunkCache.class.getDeclaredField("ticketStorage"); storageField.setAccessible(true);
        var storage = (TicketStorage) storageField.get(world.getChunkSource());
        var chunk = new ChunkPos(16, 16);
        java.util.function.BooleanSupplier hasTicket = () -> storage.getTickets(chunk.pack()).stream()
                .anyMatch(t -> { try { return t.getType() == ticket.get(null); }
                    catch (IllegalAccessException e) { throw new RuntimeException(e); } });
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "search-next"), false);
        var second = new ServerPlayer(server, world, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, second, cookie);
        second.teleportTo(bedroom, originalPosition.x, originalPosition.y, originalPosition.z, Set.of(), 0, 0, false);
        second.setGameMode(GameType.ADVENTURE);
        try {
            border.setCenter(256.5, 256.5); border.setSize(16);
            boolean inside = true;
            for (int i=0;i<200;i++) {
                var pos = (BlockPos) sample.invoke(null, world);
                inside &= pos != null && border.isWithinBounds(pos)
                        && (long)pos.getX()*pos.getX() + (long)pos.getZ()*pos.getZ() <= 3000L*3000;
            }
            check.accept(inside, "small offset border candidates are valid before chunk loading");
            border.setCenter(5000, 5000);
            MTDream.select(player, bed, null); tick.invoke(null, server);
            check.accept(searches.isEmpty() && pending.get(null)==null,
                    "disjoint border ends search without scheduling a chunk");
            border.setCenter(2999, 2999); border.setSize(2);
            check.accept(sample.invoke(null, world)==null, "rectangle outside the search circle is rejected");

            border.setCenter(0,0); border.setSize(60_000_000);
            var random = world.getRandom();
            random.setSeed(14781);
            List<BlockPos> expected = new ArrayList<>();
            for (int i=0;i<32;i++) {
                int x,z;
                do { x=random.nextInt(6001)-3000; z=random.nextInt(6001)-3000; }
                while ((long)x*x+(long)z*z>3000L*3000);
                expected.add(new BlockPos(x,0,z));
            }
            random.setSeed(14781);
            List<BlockPos> actual = new ArrayList<>();
            for (int i=0;i<32;i++) actual.add((BlockPos)sample.invoke(null,world));
            check.accept(actual.equals(expected), "unrestricted border retains original candidate and RNG order");
            var randomField = Level.class.getDeclaredField("random"); randomField.setAccessible(true);
            int[] draws = {0};
            randomField.set(world, Proxy.newProxyInstance(RandomSource.class.getClassLoader(), new Class<?>[]{RandomSource.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("nextInt") && args!=null && args.length==1) {
                            draws[0]++; return (int)args[0]-1;
                        }
                        return method.invoke(random,args);
                    }));
            try {
                check.accept(sample.invoke(null,world)==null && draws[0]==256,
                        "rejected coordinate sampling stops after 128 pairs");
            } finally { randomField.set(world, random); }

            // One real loaded column makes queue outcomes deterministic without waiting on generation.
            border.setCenter(256.5,256.5); border.setSize(1);
            world.setBlockAndUpdate(new BlockPos(256,199,256), Blocks.STONE.defaultBlockState());
            world.setBlockAndUpdate(new BlockPos(256,200,256), Blocks.AIR.defaultBlockState());
            world.setBlockAndUpdate(new BlockPos(256,201,256), Blocks.AIR.defaultBlockState());
            queue(server,bed,player,second);
            var future = new CompletableFuture<>();
            Object waiting = constructor.newInstance(player.getUUID(), searches.get(player.getUUID()),
                    256,256,System.nanoTime(),future,false);
            pending.set(null, waiting);
            world.getChunkSource().addTicketWithRadius((TicketType)ticket.get(null),chunk,0);
            nextTick(tick,server);
            check.accept(pending.get(null)==waiting && hasTicket.getAsBoolean(),
                    "unfinished load before deadline retains its search ticket");
            pending.set(null,constructor.newInstance(player.getUUID(), searches.get(player.getUUID()),
                    256,256,System.nanoTime()-timeout.getLong(null)-1,future,false));
            nextTick(tick,server);
            check.accept(pending.get(null)==null && !searches.containsKey(player.getUUID()) && !hasTicket.getAsBoolean(),
                    "expired load removes the waiting player and releases its ticket");
            future.complete(null); nextTick(tick,server);
            check.accept(!searches.containsKey(second.getUUID()) && second.level()==world && player.level()==bedroom,
                    "late completion cannot teleport the timed-out player; next player proceeds");
            release.invoke(null,world); searches.clear();
            second.teleportTo(bedroom,originalPosition.x,originalPosition.y,originalPosition.z,Set.of(),0,0,false);
            second.setGameMode(GameType.ADVENTURE);

            queue(server,bed,player); player.setGameMode(GameType.SPECTATOR); nextTick(tick,server);
            check.accept(searches.isEmpty() && pending.get(null)==null && player.isSpectator(),
                    "spectator queued before loading is cancelled without changing mode");
            player.setGameMode(GameType.ADVENTURE);
            queue(server,bed,player);
            pending.set(null,constructor.newInstance(player.getUUID(),searches.get(player.getUUID()),
                    256,256,System.nanoTime(),new CompletableFuture<>(),true));
            world.getChunkSource().addTicketWithRadius((TicketType)ticket.get(null),chunk,0);
            player.setGameMode(GameType.SPECTATOR); nextTick(tick,server);
            check.accept(searches.isEmpty() && pending.get(null)==null && player.isSpectator()
                    && player.level()==bedroom && !hasTicket.getAsBoolean(),
                    "spectator during fast loading is cancelled and its ticket is released");

            player.setGameMode(GameType.ADVENTURE);
            world.setBlockAndUpdate(new BlockPos(256,199,256), Blocks.MAGMA_BLOCK.defaultBlockState());
            queue(server,bed,player,second);
            pending.set(null,constructor.newInstance(player.getUUID(), searches.get(player.getUUID()),
                    256,256,System.nanoTime(),CompletableFuture.completedFuture(null),false));
            nextTick(tick,server);
            check.accept(searches.firstEntry().getKey().equals(second.getUUID()), "unsafe candidate rotates player to queue tail");
            var sharedAttempts=searches.get(player.getUUID()).getClass().getDeclaredMethod("attempts"); sharedAttempts.setAccessible(true);
            tick.invoke(null,server);
            check.accept((int)sharedAttempts.invoke(searches.get(player.getUUID()))
                    +(int)sharedAttempts.invoke(searches.get(second.getUUID()))<=4,
                    "cold completion and fast continuation share the four-candidate limit");
            searches.clear(); queue(server,bed,player);
            for (int i=0;i<32;i++) {
                pending.set(null,constructor.newInstance(player.getUUID(), searches.get(player.getUUID()),
                        256,256,System.nanoTime(),CompletableFuture.completedFuture(null),false));
                nextTick(tick,server);
                if (i==30) check.accept(searches.containsKey(player.getUUID()), "unsafe search retains the full 32-attempt budget");
            }
            check.accept(searches.isEmpty() && pending.get(null)==null && !hasTicket.getAsBoolean(),
                    "32 unsafe candidates exhaust the search and release its ticket");

            queue(server,bed,player);
            nextTick(tick,server);
            var attempts=searches.get(player.getUUID()).getClass().getDeclaredMethod("attempts"); attempts.setAccessible(true);
            int firstBatch=(int)attempts.invoke(searches.get(player.getUUID()));
            check.accept(firstBatch>0 && firstBatch<=4, "ready-candidate batch never exceeds four attempts");
            tick.invoke(null,server);
            check.accept((int)attempts.invoke(searches.get(player.getUUID()))==firstBatch,
                    "repeated entry/tick work shares the same per-tick budget");
            release.invoke(null,world); searches.clear();

            world.setBlockAndUpdate(new BlockPos(256,199,256), Blocks.STONE.defaultBlockState());
            queue(server,bed,player);
            pending.set(null,constructor.newInstance(player.getUUID(), searches.get(player.getUUID()),
                    256,256,System.nanoTime()-timeout.getLong(null)-1,CompletableFuture.completedFuture(null),false));
            nextTick(tick,server);
            check.accept(player.level()==world && player.gameMode()==GameType.SURVIVAL && !hasTicket.getAsBoolean(),
                    "completed safe load succeeds even when the wait deadline has passed");
            player.teleportTo(bedroom,originalPosition.x,originalPosition.y,originalPosition.z,Set.of(),0,0,false);
            player.setGameMode(GameType.ADVENTURE);
            resetBudget(); MTDream.select(player,bed,null);
            check.accept(player.level()==world && searches.isEmpty() && pending.get(null)==null && !hasTicket.getAsBoolean(),
                    "loaded safe candidate completes at entry and releases its ticket");
            player.teleportTo(bedroom,originalPosition.x,originalPosition.y,originalPosition.z,Set.of(),0,0,false);
            player.setGameMode(GameType.ADVENTURE);

            border.setCenter(2500.5,0.5); border.setSize(1);
            ChunkPos cold=new ChunkPos(2500>>4,0);
            check.accept(world.getChunkSource().getChunkNow(cold.x(),cold.z())==null, "cold fixture starts unloaded");
            resetBudget(); MTDream.select(player,bed,null);
            Object deferred=pending.get(null);
            var ready=deferred.getClass().getDeclaredMethod("ready"); ready.setAccessible(true);
            var fast=deferred.getClass().getDeclaredMethod("fast"); fast.setAccessible(true);
            check.accept(!(boolean)fast.invoke(deferred) && ready.invoke(deferred)==null
                    && storage.getTickets(cold.pack()).stream().noneMatch(t->t.getType()==searchTicket),
                    "unknown candidate is reserved without loading or a ticket at entry");
            nextTick(tick,server);
            check.accept(pending.get(null)!=null && ready.invoke(pending.get(null))!=null && player.level()==bedroom,
                    "cold request starts on the regular tick and is not consumed in its scheduling tick");
        } finally {
            release.invoke(null,world); searches.clear();
            border.setCenter(centerX,centerZ); border.setSize(size);
            player.teleportTo(bedroom, originalPosition.x, originalPosition.y, originalPosition.z, Set.of(),0,0,false);
            player.setGameMode(GameType.ADVENTURE);
            server.getPlayerList().remove(second);
            resetBudget();
        }
    }

    private static void queue(MinecraftServer server,BlockPos bed,ServerPlayer... players) throws Exception {
        var workTick=MTDream.class.getDeclaredField("workTick"); workTick.setAccessible(true);
        var work=MTDream.class.getDeclaredField("workThisTick"); work.setAccessible(true);
        workTick.setInt(null,server.getTickCount()); work.setLong(null,2_000_000);
        for (var player:players) MTDream.select(player,bed,null);
    }

    private static void resetBudget() throws Exception {
        var workTick=MTDream.class.getDeclaredField("workTick"); workTick.setAccessible(true); workTick.setInt(null,-1);
    }

    private static void nextTick(java.lang.reflect.Method tick,MinecraftServer server) throws Exception {
        resetBudget(); tick.invoke(null,server);
    }
}
