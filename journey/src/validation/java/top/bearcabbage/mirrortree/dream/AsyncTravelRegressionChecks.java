package top.bearcabbage.mirrortree.dream;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import top.bearcabbage.mirrortree.MirrorTree;

/** Uses only a disposable validation world; fake futures make cancellation/deadline checks deterministic. */
public final class AsyncTravelRegressionChecks {
    public static void resetBudget() throws Exception {
        var tick = MTDream.class.getDeclaredField("workTick"); tick.setAccessible(true); tick.setInt(null, -1);
    }

    @SuppressWarnings("unchecked")
    public static void run(MinecraftServer server, BiConsumer<Boolean, String> check) throws Exception {
        var bedroom = server.getLevel(MirrorTree.bedroom);
        var overworld = server.overworld();
        var border = overworld.getWorldBorder();
        double borderX = border.getCenterX(), borderZ = border.getCenterZ(), borderSize = border.getSize();
        var source = new BlockPos(20, 200, 20);
        var bed = new BlockPos(4241, 66, 799);
        var wake = MTDream.class.getDeclaredField("wakePosition"); wake.setAccessible(true);
        var originalWake = (BlockPos) wake.get(null);
        var pending = MTDream.class.getDeclaredField("pendingSearch"); pending.setAccessible(true);
        var constructor = pending.getType().getDeclaredConstructors()[0]; constructor.setAccessible(true);
        var queue = MTDream.class.getDeclaredField("SEARCHES"); queue.setAccessible(true);
        var searches = (Map<UUID, Object>) queue.get(null);
        var tick = MTDream.class.getDeclaredMethod("tick", MinecraftServer.class); tick.setAccessible(true);
        var cancel = MTDream.class.getDeclaredMethod("cancelSearch", UUID.class, MinecraftServer.class); cancel.setAccessible(true);
        var ticketField = MTDream.class.getDeclaredField("SEARCH_TICKET"); ticketField.setAccessible(true);
        var ticket = (TicketType) ticketField.get(null);
        var player = top.bearcabbage.mirrortree.validation.ProbePlayers.join(server, "async-travel");
        var file = server.getWorldPath(LevelResource.ROOT).resolve("mirrortree/dreams/" + player.getUUID() + ".json");
        class Fixture {
            void defer() throws Exception {
                var workTick = MTDream.class.getDeclaredField("workTick"); workTick.setAccessible(true);
                var work = MTDream.class.getDeclaredField("workThisTick"); work.setAccessible(true);
                workTick.setInt(null, server.getTickCount()); work.setLong(null, 2_000_000);
            }
            void nextTick() throws Exception { resetBudget(); tick.invoke(null, server); }
            void sleep() {
                player.teleportTo(overworld, 20.5, 200, 20.5, Set.of(), 0, 0, false);
                player.setGameMode(GameType.SURVIVAL);
                player.startSleeping(source);
            }
            void waitAt(ServerLevel world, BlockPos center, CompletableFuture<?> future, boolean expired) throws Exception {
                var request = searches.get(player.getUUID());
                var travelAccessor = request.getClass().getDeclaredMethod("travel"); travelAccessor.setAccessible(true);
                var travel = travelAccessor.invoke(request);
                var ticketsAccessor = travel.getClass().getDeclaredMethod("tickets"); ticketsAccessor.setAccessible(true);
                var chunk = new ChunkPos(center.getX() >> 4, center.getZ() >> 4);
                ((Set<ChunkPos>) ticketsAccessor.invoke(travel)).add(chunk);
                world.getChunkSource().addTicketWithRadius(ticket, chunk, 0);
                pending.set(null, constructor.newInstance(player.getUUID(), request, center.getX(), center.getZ(),
                        System.nanoTime() - (expired ? 31_000_000_000L : 0), future, true));
            }
            boolean hasTicket(ServerLevel world, BlockPos center) throws Exception {
                var storageField = ServerChunkCache.class.getDeclaredField("ticketStorage"); storageField.setAccessible(true);
                var storage = (TicketStorage) storageField.get(world.getChunkSource());
                return storage.getTickets(new ChunkPos(center.getX() >> 4, center.getZ() >> 4).pack()).stream().anyMatch(t -> t.getType() == ticket);
            }
        }
        var fixture = new Fixture();
        try {
            platform(overworld, source);
            platform(bedroom, originalWake);
            overworld.setBlockAndUpdate(source, Blocks.RED_BED.defaultBlockState());
            bedroom.setBlockAndUpdate(bed, Blocks.RED_BED.defaultBlockState());
            DreamStore.save(player, new DreamStore.Snapshot());
            byte[] before = Files.readAllBytes(file);

            // Unknown chunks must remain untouched at command entry; this cancels before scheduling generation.
            var cold = new BlockPos(12000, 200, 12000);
            check.accept(bedroom.getChunkSource().getChunkNow(cold.getX() >> 4, cold.getZ() >> 4) == null,
                    "async travel: destination starts unloaded");
            wake.set(null, cold); fixture.sleep(); resetBudget();
            check.accept(MTDream.wakeup(player), "async travel: cold wakeup request accepted");
            var reserved = pending.get(null);
            var ready = reserved.getClass().getDeclaredMethod("ready"); ready.setAccessible(true);
            check.accept(ready.invoke(reserved) == null && !fixture.hasTicket(bedroom, cold)
                    && bedroom.getChunkSource().getChunkNow(cold.getX() >> 4, cold.getZ() >> 4) == null
                    && player.isSleeping() && Arrays.equals(before, Files.readAllBytes(file)),
                    "async travel: command entry neither loads cold chunks nor changes sleep or durable state");
            player.stopSleepInBed(true, true); fixture.nextTick();
            check.accept(!searches.containsKey(player.getUUID()) && pending.get(null) == null,
                    "async travel: leaving bed cancels before a cold load starts");
            wake.set(null, originalWake);

            fixture.sleep(); fixture.defer(); MTDream.wakeup(player);
            var incomplete = new CompletableFuture<>();
            fixture.waitAt(bedroom, originalWake, incomplete, false); fixture.nextTick();
            check.accept(player.level() == overworld && fixture.hasTicket(bedroom, originalWake)
                    && Arrays.equals(before, Files.readAllBytes(file)),
                    "async travel: unfinished destination preserves snapshot and holds its own-dimension ticket");
            fixture.waitAt(bedroom, originalWake, incomplete, true); fixture.nextTick();
            incomplete.complete(null); fixture.nextTick();
            check.accept(player.level() == overworld && !fixture.hasTicket(bedroom, originalWake)
                    && !searches.containsKey(player.getUUID()) && Arrays.equals(before, Files.readAllBytes(file)),
                    "async travel: timeout releases bedroom ticket and late completion cannot teleport or save");

            fixture.defer(); MTDream.wakeup(player);
            var completed = new CompletableFuture<>();
            fixture.waitAt(bedroom, originalWake, completed, false);
            player.setHealth(9);
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 500));
            completed.complete(null); fixture.nextTick();
            check.accept(player.level() == bedroom && DreamStore.load(player).health == 9
                    && !player.hasEffect(MobEffects.SPEED) && !fixture.hasTicket(bedroom, originalWake),
                    "async travel: ready wakeup captures current state before moving and releases tickets");
            before = Files.readAllBytes(file);

            fixture.defer(); MTDream.enter(player, bed);
            fixture.waitAt(overworld, source, new CompletableFuture<>(), false);
            player.setGameMode(GameType.SPECTATOR); fixture.nextTick();
            check.accept(player.level() == bedroom && !fixture.hasTicket(overworld, source)
                    && Arrays.equals(before, Files.readAllBytes(file)),
                    "async travel: spectator cancels old-dream return without consuming the snapshot");
            player.setGameMode(GameType.ADVENTURE); resetBudget(); MTDream.enter(player, bed);
            check.accept(player.level() == overworld && player.hasEffect(MobEffects.SPEED)
                    && player.getHealth() == 9 && !DreamStore.load(player).inBedroom,
                    "async travel: ready old-dream return restores the queued wakeup snapshot");

            var reset = MTDream.class.getDeclaredMethod("redream", ServerPlayer.class); reset.setAccessible(true);
            var resetCenter = new BlockPos(MirrorTree.config.getInt("bedroomX_init", 4258),
                    MirrorTree.config.getInt("bedroomY_init", 68), MirrorTree.config.getInt("bedroomZ_init", 796));
            before = Files.readAllBytes(file);
            fixture.defer(); reset.invoke(null, player);
            fixture.waitAt(bedroom, resetCenter, new CompletableFuture<>(), true); fixture.nextTick();
            check.accept(player.level() == overworld && !fixture.hasTicket(bedroom, resetCenter)
                    && Arrays.equals(before, Files.readAllBytes(file)),
                    "async travel: timed-out reset keeps the previous dream record");
            fixture.defer(); reset.invoke(null, player);
            fixture.waitAt(bedroom, resetCenter, new CompletableFuture<>(), false);
            player.setGameMode(GameType.SPECTATOR); fixture.nextTick();
            check.accept(player.level() == overworld && !fixture.hasTicket(bedroom, resetCenter)
                    && !searches.containsKey(player.getUUID()) && Arrays.equals(before, Files.readAllBytes(file)),
                    "async travel: spectator cancels a queued reset without changing the dream record");
            player.setGameMode(GameType.SURVIVAL);

            // A missing earlier column must be prepared before a later safe position is selected.
            var edge = new BlockPos(1600, 200, 1600);
            for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) for (int y = -4; y <= 4; y++)
                overworld.setBlockAndUpdate(edge.offset(x, y, z), Blocks.STONE.defaultBlockState());
            var later = edge.east();
            overworld.setBlockAndUpdate(later, Blocks.AIR.defaultBlockState());
            overworld.setBlockAndUpdate(later.above(), Blocks.AIR.defaultBlockState());
            var find = MTDream.class.getDeclaredMethod("nearbyLanding", ServerLevel.class, BlockPos.class, boolean.class);
            find.setAccessible(true);
            var landing = find.invoke(null, overworld, edge, true);
            var missing = landing.getClass().getDeclaredMethod("missing"); missing.setAccessible(true);
            var position = landing.getClass().getDeclaredMethod("position"); position.setAccessible(true);
            var needed = (BlockPos) missing.invoke(landing);
            check.accept(needed != null && position.invoke(landing) == null
                    && overworld.getChunkSource().getChunkNow(needed.getX() >> 4, needed.getZ() >> 4) == null
                    && position.invoke(find.invoke(null, overworld, edge, false)) != null,
                    "async travel: missing earlier candidate is reported without loading or skipping to later safety");

            player.teleportTo(bedroom, 4239.5, 66, 799.5, Set.of(), 0, 0, false);
            player.setGameMode(GameType.ADVENTURE);
            var borderEdge = new BlockPos(3200, 200, 3200);
            overworld.setBlockAndUpdate(borderEdge.below(), Blocks.STONE.defaultBlockState());
            overworld.setBlockAndUpdate(borderEdge, Blocks.AIR.defaultBlockState());
            overworld.setBlockAndUpdate(borderEdge.above(), Blocks.AIR.defaultBlockState());
            var outside = borderEdge.west();
            var snapshot = new DreamStore.Snapshot();
            snapshot.inBedroom = true;
            snapshot.x = outside.getX() + .5; snapshot.y = outside.getY(); snapshot.z = outside.getZ() + .5;
            DreamStore.save(player, snapshot);
            border.setCenter(3202.5, 3202.5); border.setSize(5);
            check.accept(!border.isWithinBounds(outside) && border.isWithinBounds(borderEdge),
                    "async travel: shrunken border excludes the old center but retains nearby safety");
            resetBudget(); MTDream.enter(player, bed);
            check.accept(player.level() == overworld && player.blockPosition().equals(borderEdge),
                    "async travel: shrunken border still returns to legal nearby safety");
        } finally {
            border.setCenter(borderX, borderZ); border.setSize(borderSize);
            wake.set(null, originalWake);
            cancel.invoke(null, player.getUUID(), server);
            server.getPlayerList().remove(player);
            resetBudget();
        }
    }

    private static void platform(ServerLevel level, BlockPos pos) {
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            level.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y < 3; y++) level.setBlockAndUpdate(pos.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
    }
}
