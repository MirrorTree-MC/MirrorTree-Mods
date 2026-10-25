package top.bearcabbage.mirrortree.dream;

import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import top.bearcabbage.lanterninstorm.LanternInStormAPI;
import top.bearcabbage.mirrortree.MirrorTree;
import top.bearcabbage.mirrortree.mixin.ChunkMapAccessor;
import top.bearcabbage.mirrortree.settlement.DreamSelectionMenu;
import top.bearcabbage.mirrortree.settlement.SettlementCommands;
import top.bearcabbage.mirrortree.settlement.SettlementWarps;

public final class MTDream {
    private static final LinkedHashMap<UUID, Search> SEARCHES = new LinkedHashMap<>();
    private static final TicketType SEARCH_TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE,
            Identifier.fromNamespaceAndPath("mirrortree", "dream_search"),
            new TicketType(40, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
    private static final int RANGE = 3000, MAX_ATTEMPTS = 32, MAX_POSITION_DRAWS = 128, MAX_CANDIDATES_PER_TICK = 4;
    private static final long CHUNK_WAIT_NANOS = 30_000_000_000L;
    private record Search(BlockPos bed, int attempts, SettlementWarps.Settlement settlement) {
        Search(BlockPos bed, int attempts) { this(bed, attempts, null); }
    }
    private record PendingSearch(UUID player, Search search, int x, int z, long startedNanos, CompletableFuture<?> ready, boolean fast) {
        ChunkPos chunk() { return new ChunkPos(x >> 4, z >> 4); }
    }
    private static PendingSearch pendingSearch;
    private static int workTick = -1, candidatesThisTick;
    private static long workThisTick;
    private static BlockPos wakePosition;

    public static void init() {
        SettlementCommands.init();
        wakePosition = new BlockPos(MirrorTree.config.getInt("bedroomX", 4239),
                MirrorTree.config.getInt("bedroomY", 66), MirrorTree.config.getInt("bedroomZ", 799));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(Commands.literal("wakeup").executes(context -> {
                var player = context.getSource().getPlayer();
                return player != null && wakeup(player) ? 1 : 0;
            }));
            dispatcher.register(Commands.literal("redream")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .executes(context -> {
                        var player = context.getSource().getPlayer();
                        return player != null && redream(player) ? 1 : 0;
                    }));
        });
        EntitySleepEvents.START_SLEEPING.register((entity, bed) -> {
            if (entity instanceof ServerPlayer player && player.level().dimension().equals(Level.OVERWORLD))
                player.sendSystemMessage(Component.literal("⏰ 要试着醒来吗？ [回到卧室]")
                        .withColor(0xDDD605).withStyle(style ->
                                style.withClickEvent(new ClickEvent.RunCommand("/wakeup"))));
        });
        ServerTickEvents.END_SERVER_TICK.register(MTDream::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SEARCHES.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            releaseSearch(server.overworld());
            SEARCHES.clear();
            workTick = -1;
            candidatesThisTick = 0;
            workThisTick = 0;
        });
    }

    public static void enter(ServerPlayer player, BlockPos bed) {
        if (!canChoose(player, bed)) return;
        try {
            var snapshot = DreamStore.load(player);
            if (snapshot.inBedroom) {
                var key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(snapshot.dimension));
                var destination = player.level().getServer().getLevel(key);
                BlockPos safe = destination == null ? null
                        : nearbySafe(destination, BlockPos.containing(snapshot.x, snapshot.y, snapshot.z));
                if (safe == null) {
                    player.sendSystemMessage(Component.literal("原入梦位置暂时不安全，请联系管理员；返回记录已保留。"));
                    return;
                }
                if (player.teleportTo(destination, safe.getX() + .5, safe.getY(), safe.getZ() + .5,
                        Set.of(), snapshot.yaw, snapshot.pitch, false)) {
                    if (!player.isCreative()) player.setGameMode(GameType.SURVIVAL);
                    snapshot.restore(player);
                    snapshot.inBedroom = false;
                    DreamStore.save(player, snapshot);
                    player.sendSystemMessage(Component.literal("你回到了梦中。"));
                }
            } else {
                DreamSelectionMenu.open(player, bed);
            }
        } catch (Exception e) {
            MirrorTree.LOGGER.error("Cannot enter dream for {}", player.getUUID(), e);
            player.sendSystemMessage(Component.literal("入梦数据读取失败，原记录已保留，请联系管理员。"));
        }
    }

    public static boolean canChoose(ServerPlayer player, BlockPos bed) {
        return validBed(player, bed) && !SEARCHES.containsKey(player.getUUID());
    }

    /** Revalidate the server-owned selection rather than trusting a menu item or old warp snapshot. */
    public static boolean select(ServerPlayer player, BlockPos bed, String settlementName) {
        if (!canChoose(player, bed)) return false;
        try {
            if (DreamStore.load(player).inBedroom) return false;
            var settlement = settlementName == null ? null : SettlementWarps.resolve(player, settlementName);
            if (settlementName != null && settlement == null) {
                player.sendSystemMessage(Component.literal("这个聚落已删除或不可访问，请重新选择。"));
                return false;
            }
            if (settlement != null && !player.level().getServer().overworld().getWorldBorder().isWithinBounds(settlement.blockPos())) {
                player.sendSystemMessage(Component.literal("聚落位置超出世界边界，请联系管理员。"));
                return false;
            }
            var overworld = player.level().getServer().overworld();
            if (settlement != null && (settlement.blockPos().getY() <= overworld.getMinY()
                    || settlement.blockPos().getY() + 1 >= overworld.getMaxY())) {
                player.sendSystemMessage(Component.literal("聚落位置超出世界高度范围，请联系管理员。"));
                return false;
            }
            SEARCHES.put(player.getUUID(), new Search(bed.immutable(), 0, settlement));
            player.sendSystemMessage(Component.literal(settlement == null
                    ? "你躺在床上，闭上了眼睛……正在寻找安全的入梦点。"
                    : "正在前往聚落「" + settlement.displayName() + "」……"));
            drainSearch(player.level().getServer(), false);
            return true;
        } catch (Exception e) {
            MirrorTree.LOGGER.error("Cannot select dream for {}", player.getUUID(), e);
            player.sendSystemMessage(Component.literal("入梦数据读取失败，原记录已保留，请联系管理员。"));
            return false;
        }
    }

    public static boolean wakeup(ServerPlayer player) {
        if (!player.isSleeping() || !player.level().dimension().equals(Level.OVERWORLD)) return false;
        var bedroom = player.level().getServer().getLevel(MirrorTree.bedroom);
        BlockPos safe = bedroom == null ? null : nearbySafe(bedroom, wakePosition);
        if (safe == null) {
            player.sendSystemMessage(Component.literal("卧室落点暂时不可用，请联系管理员。"));
            return false;
        }
        try {
            player.stopSleepInBed(true, true);
            var snapshot = DreamStore.Snapshot.capture(player);
            DreamStore.save(player, snapshot);
            if (!player.teleportTo(bedroom, safe.getX() + .5, safe.getY(), safe.getZ() + .5,
                    Set.of(), 0, 0, false)) {
                snapshot.inBedroom = false;
                DreamStore.save(player, snapshot);
                return false;
            }
            player.removeAllEffects();
            if (!player.isCreative()) player.setGameMode(GameType.ADVENTURE);
            player.sendSystemMessage(Component.literal("你醒来了。右键卧室的床可以返回梦境。"));
            return true;
        } catch (Exception e) {
            MirrorTree.LOGGER.error("Cannot save dream for {}", player.getUUID(), e);
            player.sendSystemMessage(Component.literal("保存入梦记录失败，请联系管理员。"));
            return false;
        }
    }

    private static void tick(MinecraftServer server) {
        for (var player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(MirrorTree.bedroom)
                    && !player.isCreative() && !player.isSpectator()
                    && player.gameMode() != GameType.ADVENTURE) player.setGameMode(GameType.ADVENTURE);
        }
        drainSearch(server, true);
    }

    private static void drainSearch(MinecraftServer server, boolean regularTick) {
        if (workTick != server.getTickCount()) {
            workTick = server.getTickCount();
            candidatesThisTick = 0;
            workThisTick = 0;
        }
        while (candidatesThisTick < MAX_CANDIDATES_PER_TICK && workThisTick < 2_000_000) {
            long start = System.nanoTime();
            boolean advanced;
            try { advanced = advanceSearch(server, regularTick); }
            finally { workThisTick += System.nanoTime() - start; }
            if (!advanced) break;
        }
    }

    private static boolean advanceSearch(MinecraftServer server, boolean regularTick) {
        ServerLevel world = server.overworld();
        if (pendingSearch == null) {
            while (!SEARCHES.isEmpty()) {
                var first = SEARCHES.firstEntry();
                if (validSearch(server.getPlayerList().getPlayer(first.getKey()), first.getValue())) break;
                SEARCHES.pollFirstEntry();
            }
            if (SEARCHES.isEmpty()) return false;
            var first = SEARCHES.firstEntry();
            var settlement = first.getValue().settlement();
            BlockPos position = settlement == null ? randomCandidate(world) : settlement.blockPos();
            if (position == null) {
                SEARCHES.remove(first.getKey());
                server.getPlayerList().getPlayer(first.getKey()).sendSystemMessage(
                        Component.literal("当前世界边界内未能选出入梦点，请稍后再试。"));
                return true;
            }
            int x = position.getX(), z = position.getZ();
            ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
            // The vanilla cache uses 1 for a full chunk. Unknown/proto chunks keep the original tick path.
            boolean fast = world.getChunkSource().getChunkNow(x >> 4, z >> 4) != null
                    || ((ChunkMapAccessor) world.getChunkSource().chunkMap)
                            .mirrortree$getChunkTypeCache().get(chunk.pack()) == 1;
            // A null future reserves the candidate until its loading path may run.
            pendingSearch = new PendingSearch(first.getKey(), first.getValue(), x, z, 0, null, fast);
        }
        PendingSearch pending = pendingSearch;
        if (!pending.fast() && !regularTick) return false;
        var player = server.getPlayerList().getPlayer(pending.player());
        Search search = pending.search();
        if (SEARCHES.get(pending.player()) != search || !validSearch(player, search)) {
            // A newly queued record may equal the cancelled one by value; preserve its identity.
            if (SEARCHES.get(pending.player()) == search) SEARCHES.remove(pending.player());
            releaseSearch(world);
            return pending.fast();
        }
        if (search.settlement() != null
                && (!search.settlement().equals(SettlementWarps.resolve(player, search.settlement().name()))
                    || !world.getWorldBorder().isWithinBounds(search.settlement().blockPos()))) {
            SEARCHES.remove(pending.player());
            releaseSearch(world);
            player.sendSystemMessage(Component.literal("聚落位置或访问权限已变化，请重新选择。"));
            return pending.fast();
        }
        if (pending.ready() == null) {
            try {
                long startedNanos = System.nanoTime();
                CompletableFuture<?> ready;
                if (pending.fast() && world.getChunkSource().getChunkNow(pending.x() >> 4, pending.z() >> 4) != null) {
                    world.getChunkSource().addTicketWithRadius(SEARCH_TICKET, pending.chunk(), 0);
                    ready = CompletableFuture.completedFuture(null);
                } else {
                    // Schedule without managedBlock; cold/unknown chunks start only on END_SERVER_TICK.
                    ready = world.getChunkSource().addTicketAndLoadWithRadius(SEARCH_TICKET, pending.chunk(), 0);
                }
                PendingSearch scheduled = new PendingSearch(pending.player(), search, pending.x(), pending.z(),
                        startedNanos, ready, pending.fast());
                pendingSearch = pending = scheduled;
                if (!pending.fast()) return false;
                if (!ready.isDone()) ready.whenComplete((unused, error) ->
                        server.schedule(new net.minecraft.server.TickTask(server.getTickCount(), () -> {
                            if (pendingSearch == scheduled) drainSearch(server, false);
                        })));
            } catch (RuntimeException error) {
                SEARCHES.remove(pending.player());
                releaseSearch(world);
                MirrorTree.LOGGER.error("Cannot schedule dream candidate for {}", pending.player(), error);
                player.sendSystemMessage(Component.literal("入梦区块加载失败，请稍后再试。"));
                return pending.fast();
            }
        }
        world.getChunkSource().addTicketWithRadius(SEARCH_TICKET, pending.chunk(), 0);
        if (!pending.ready().isDone()) {
            if (System.nanoTime() - pending.startedNanos() >= CHUNK_WAIT_NANOS) {
                SEARCHES.remove(pending.player());
                releaseSearch(world);
                player.sendSystemMessage(Component.literal("入梦区块加载超时，请稍后再试。"));
            }
            return pending.fast() && pendingSearch == null;
        }
        candidatesThisTick++;
        try {
            pending.ready().join(); // Completed above: never wait for generation on the tick thread.
            int x = pending.x(), z = pending.z();
            if (world.getChunkSource().getChunkNow(x >> 4, z >> 4) == null)
                throw new IllegalStateException("Dream candidate chunk did not finish loading");
            var settlement = search.settlement();
            BlockPos candidate = settlement == null
                    ? new BlockPos(x, world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z)
                    : nearbySafe(world, settlement.blockPos(), true);
            SEARCHES.remove(pending.player());
            if (candidate != null && safe(world, candidate)) {
                float yaw = settlement == null ? 0 : settlement.yaw(), pitch = settlement == null ? 0 : settlement.pitch();
                if (player.teleportTo(world, candidate.getX() + .5, candidate.getY(), candidate.getZ() + .5,
                        Set.of(), yaw, pitch, false)) {
                    if (!player.isCreative()) player.setGameMode(GameType.SURVIVAL);
                    player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                            LevelData.RespawnData.of(Level.OVERWORLD, candidate, yaw, pitch), true), false);
                    LanternInStormAPI.setRTPSpawn(player, candidate, settlement != null);
                    if (settlement != null) {
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                                Component.literal("大鹏的梦").withColor(0x525288)));
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                                Component.literal("聚落「" + settlement.displayName() + "」是你的起点")));
                    }
                }
            } else if (settlement != null) {
                player.sendSystemMessage(Component.literal("聚落附近暂时没有安全落点，请联系管理员，或选择随机入梦。"));
            } else if (search.attempts() + 1 >= MAX_ATTEMPTS) {
                player.sendSystemMessage(Component.literal("这次没有找到安全入梦点，请再试一次。"));
            } else SEARCHES.put(pending.player(), new Search(search.bed(), search.attempts() + 1));
        } catch (RuntimeException error) {
            SEARCHES.remove(pending.player());
            MirrorTree.LOGGER.error("Cannot load dream candidate for {}", pending.player(), error);
            player.sendSystemMessage(Component.literal("入梦区块加载失败，请稍后再试。"));
        } finally {
            releaseSearch(world);
        }
        return pending.fast();
    }

    private static BlockPos randomCandidate(ServerLevel world) {
        var border = world.getWorldBorder();
        // Match the block-column bounds used by safe(), before requesting any chunks.
        int minX = Math.max(-RANGE, (int) Math.floor(border.getMinX()));
        int maxX = Math.min(RANGE, (int) Math.ceil(border.getMaxX()) - 1);
        int minZ = Math.max(-RANGE, (int) Math.floor(border.getMinZ()));
        int maxZ = Math.min(RANGE, (int) Math.ceil(border.getMaxZ()) - 1);
        if (minX > maxX || minZ > maxZ) return null;
        int nearestX = Math.clamp(0, minX, maxX), nearestZ = Math.clamp(0, minZ, maxZ);
        if ((long) nearestX*nearestX + (long) nearestZ*nearestZ > (long) RANGE*RANGE) return null;
        for (int draw = 0; draw < MAX_POSITION_DRAWS; draw++) {
            int x = world.getRandom().nextInt(maxX - minX + 1) + minX;
            int z = world.getRandom().nextInt(maxZ - minZ + 1) + minZ;
            BlockPos candidate = new BlockPos(x, 0, z);
            if ((long) x*x + (long) z*z <= (long) RANGE*RANGE && border.isWithinBounds(candidate))
                return candidate;
        }
        return null;
    }

    private static boolean validSearch(ServerPlayer player, Search search) {
        return validBed(player, search.bed());
    }

    private static boolean validBed(ServerPlayer player, BlockPos bed) {
        return player != null && player.isAlive() && !player.isSpectator()
                && player.level().dimension().equals(MirrorTree.bedroom)
                && player.level().getBlockState(bed).getBlock() instanceof BedBlock
                && player.distanceToSqr(bed.getX() + .5, bed.getY() + .5, bed.getZ() + .5) <= 36;
    }

    private static void releaseSearch(ServerLevel world) {
        if (pendingSearch != null) {
            world.getChunkSource().removeTicketWithRadius(SEARCH_TICKET, pendingSearch.chunk(), 0);
            pendingSearch = null;
        }
    }

    private static boolean redream(ServerPlayer player) {
        var world = player.level().getServer().getLevel(MirrorTree.bedroom);
        BlockPos start = new BlockPos(MirrorTree.config.getInt("bedroomX_init", 4258),
                MirrorTree.config.getInt("bedroomY_init", 68), MirrorTree.config.getInt("bedroomZ_init", 796));
        BlockPos safe = world == null ? null : nearbySafe(world, start);
        if (safe == null) return false;
        try {
            DreamStore.save(player, new DreamStore.Snapshot());
            SEARCHES.remove(player.getUUID());
            if (!player.teleportTo(world, safe.getX() + .5, safe.getY(), safe.getZ() + .5, Set.of(), 90, 0, false))
                return false;
            player.removeAllEffects();
            if (!player.isCreative()) player.setGameMode(GameType.ADVENTURE);
            player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                    LevelData.RespawnData.of(MirrorTree.bedroom, safe, 90, 0), true), false);
            return true;
        } catch (Exception e) {
            MirrorTree.LOGGER.error("Cannot reset dream for {}", player.getUUID(), e);
            return false;
        }
    }

    private static BlockPos nearbySafe(ServerLevel world, BlockPos center) {
        return nearbySafe(world, center, false);
    }

    private static BlockPos nearbySafe(ServerLevel world, BlockPos center, boolean loadedOnly) {
        for (int radius = 0; radius <= 4; radius++) {
            for (int y = 0; y <= 3; y++) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        // Inner positions were already checked at an earlier radius.
                        if (radius > 0 && Math.abs(x) < radius && Math.abs(z) < radius) continue;
                        BlockPos pos = center.offset(x, y, z);
                        if ((!loadedOnly || world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null)
                                && safe(world, pos)) return pos;
                        if (y > 0) {
                            pos = center.offset(x, -y, z);
                            if ((!loadedOnly || world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null)
                                    && safe(world, pos)) return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean safe(ServerLevel world, BlockPos pos) {
        if (!world.getWorldBorder().isWithinBounds(pos) || pos.getY() <= world.getMinY()
                || pos.getY() + 1 >= world.getMaxY()) return false;
        var floor = world.getBlockState(pos.below());
        return floor.isFaceSturdy(world, pos.below(), net.minecraft.core.Direction.UP)
                && !floor.is(BlockTags.LEAVES) && !floor.is(BlockTags.FIRE)
                && !floor.is(Blocks.MAGMA_BLOCK) && !floor.is(Blocks.CAMPFIRE)
                && !floor.is(Blocks.SOUL_CAMPFIRE) && !floor.is(Blocks.CACTUS)
                && floor.getFluidState().isEmpty()
                && world.getBlockState(pos).isAir() && world.getBlockState(pos.above()).isAir();
    }
}
