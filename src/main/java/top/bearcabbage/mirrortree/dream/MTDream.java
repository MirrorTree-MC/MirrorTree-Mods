package top.bearcabbage.mirrortree.dream;

import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import top.bearcabbage.lanterninstorm.LanternInStormAPI;
import top.bearcabbage.mirrortree.MirrorTree;

public final class MTDream {
    private static final Map<UUID, Search> SEARCHES = new HashMap<>();
    private static final int RANGE = 3000, MAX_ATTEMPTS = 32;
    private record Search(BlockPos bed, int attempts) {}
    private static BlockPos wakePosition;

    public static void init() {
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
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SEARCHES.clear());
    }

    public static void enter(ServerPlayer player, BlockPos bed) {
        if (!player.level().dimension().equals(MirrorTree.bedroom) || player.isSpectator()
                || !player.isAlive() || !(player.level().getBlockState(bed).getBlock() instanceof BedBlock)
                || player.distanceToSqr(bed.getX() + .5, bed.getY() + .5, bed.getZ() + .5) > 36
                || SEARCHES.containsKey(player.getUUID())) return;
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
                SEARCHES.put(player.getUUID(), new Search(bed.immutable(), 0));
                player.sendSystemMessage(Component.literal("你躺在床上，闭上了眼睛……正在寻找安全的入梦点。"));
            }
        } catch (Exception e) {
            MirrorTree.LOGGER.error("Cannot enter dream for {}", player.getUUID(), e);
            player.sendSystemMessage(Component.literal("入梦数据读取失败，原记录已保留，请联系管理员。"));
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
        for (var entry : new ArrayList<>(SEARCHES.entrySet())) {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            Search search = entry.getValue();
            if (player == null || !player.isAlive() || !player.level().dimension().equals(MirrorTree.bedroom)
                    || !(player.level().getBlockState(search.bed()).getBlock() instanceof BedBlock)
                    || player.distanceToSqr(search.bed().getX() + .5, search.bed().getY() + .5, search.bed().getZ() + .5) > 36) {
                SEARCHES.remove(entry.getKey());
                continue;
            }
            ServerLevel world = server.overworld();
            int x, z;
            do {
                x = world.getRandom().nextInt(RANGE * 2 + 1) - RANGE;
                z = world.getRandom().nextInt(RANGE * 2 + 1) - RANGE;
            } while ((long) x*x + (long) z*z > (long) RANGE*RANGE);
            // Level.getHeight returns minY for unloaded chunks in 26.1.2.
            world.getChunk(x >> 4, z >> 4);
            BlockPos candidate = new BlockPos(x, world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (safe(world, candidate)) {
                SEARCHES.remove(entry.getKey());
                if (player.teleportTo(world, x + .5, candidate.getY(), z + .5, Set.of(), 0, 0, false)) {
                    if (!player.isCreative()) player.setGameMode(GameType.SURVIVAL);
                    player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                            LevelData.RespawnData.of(Level.OVERWORLD, candidate, 0, 0), true), false);
                    LanternInStormAPI.setRTPSpawn(player, candidate, false);
                }
            } else if (search.attempts() + 1 >= MAX_ATTEMPTS) {
                SEARCHES.remove(entry.getKey());
                player.sendSystemMessage(Component.literal("这次没有找到安全入梦点，请再试一次。"));
            } else SEARCHES.put(entry.getKey(), new Search(search.bed(), search.attempts() + 1));
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
        for (int radius = 0; radius <= 4; radius++)
            for (int y = 0; y <= 3; y++)
                for (int x = -radius; x <= radius; x++)
                    for (int z = -radius; z <= radius; z++)
                        for (int dy : y == 0 ? new int[]{0} : new int[]{y, -y}) {
                            BlockPos pos = center.offset(x, dy, z);
                            if (safe(world, pos)) return pos;
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
