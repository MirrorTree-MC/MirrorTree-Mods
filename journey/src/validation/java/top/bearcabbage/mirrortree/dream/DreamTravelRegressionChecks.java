package top.bearcabbage.mirrortree.dream;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import top.bearcabbage.mirrortree.MirrorTree;

/** Disposable diagnostic only. Never installed into a real instance. */
public final class DreamTravelRegressionChecks {
    private final BiConsumer<Boolean,String> assertion;
    private ServerPlayer player;
    private final MinecraftServer server;
    private final ServerLevel bedroom;
    private final BlockPos source = new BlockPos(20,200,20);
    private final BlockPos bedroomBed = new BlockPos(4241,66,799);
    private static UUID failAfterExit;
    private static boolean listenerRegistered;

    private DreamTravelRegressionChecks(MinecraftServer server, BiConsumer<Boolean,String> assertion) {
        this.server = server;
        this.bedroom = server.getLevel(MirrorTree.bedroom);
        this.assertion = assertion;
    }

    public static void run(MinecraftServer server, BiConsumer<Boolean,String> assertion) throws Exception {
        var checks = new DreamTravelRegressionChecks(server, assertion);
        checks.runCase(checks::staleSnapshot);
        checks.runCase(checks::portalExit);
        checks.runCase(checks::invalidEffect);
        checks.runCase(checks::failedSave);
        checks.runCase(checks::failedFinalSave);
        checks.runCase(checks::rejectedTeleport);
        checks.runCase(checks::respawn);
        checks.runCase(() -> checks.deathSnapshot(false));
        checks.runCase(() -> checks.deathSnapshot(true));
        checks.runCase(checks::deathCounter);
    }
    private interface Case { void run() throws Exception; }
    private void runCase(Case action) throws Exception {
        try {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "travel-audit"), false);
            player = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            platform(server.overworld(), source);
            platform(server.overworld(), new BlockPos(40,200,40));
            platform(bedroom, new BlockPos(4239,66,799));
            server.overworld().setBlockAndUpdate(source, Blocks.RED_BED.defaultBlockState());
            bedroom.setBlockAndUpdate(bedroomBed, Blocks.RED_BED.defaultBlockState());
            action.run();
        } finally {
            if (player != null) server.getPlayerList().remove(player);
            player = null;
        }
    }
    private void check(boolean value, String message) {
        assertion.accept(value, "dream travel: " + message);
    }
    private void move(ServerLevel level, double x, double y, double z) {
        check(player.teleportTo(level,x,y,z,Set.of(),0,0,false), "fixture teleport rejected");
    }
    private Path snapshotPath() {
        return server.getWorldPath(LevelResource.ROOT).resolve("mirrortree/dreams/" + player.getUUID() + ".json");
    }
    private void wake() throws Exception {
        move(server.overworld(),20.5,200,20.5);
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(12);
        player.getFoodData().setFoodLevel(7);
        player.addEffect(new MobEffectInstance(MobEffects.SPEED,600));
        player.startSleeping(source);
        check(MTDream.wakeup(player) && player.level()==bedroom && DreamStore.load(player).inBedroom,
            "fixture wakeup failed");
        check(!player.hasEffect(MobEffects.SPEED), "fixture effect was not suspended");
    }
    private void staleSnapshot() throws Exception {
        wake();
        move(server.overworld(),40.5,200,40.5);
        check(!DreamStore.load(player).inBedroom && player.hasEffect(MobEffects.SPEED)
                && player.getHealth()==12 && player.getFoodData().getFoodLevel()==7,
                "generic exit settles snapshot and restores suspended state");
        player.setHealth(6);
        player.getFoodData().setFoodLevel(3);
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,100));
        move(bedroom,4239.5,66,799.5);
        MTDream.enter(player, bedroomBed);
        check(player.getHealth()==6 && player.getFoodData().getFoodLevel()==3
                && player.hasEffect(MobEffects.SLOWNESS) && !DreamStore.load(player).inBedroom,
                "later bedroom visit cannot replay old vitals or effects");
    }
    private void invalidEffect() throws Exception {
        wake();
        var snapshot = DreamStore.load(player);
        snapshot.effects.get(0).getAsJsonObject().addProperty("id", "audit:removed_effect");
        DreamStore.save(player, snapshot);
        MTDream.enter(player, bedroomBed);
        check(player.level()==bedroom && player.gameMode()==GameType.ADVENTURE
                && DreamStore.load(player).inBedroom && !player.hasEffect(MobEffects.SPEED),
                "invalid effect cancels bed return before changing dimension or state");
        check(!player.teleportTo(server.overworld(),40.5,200,40.5,Set.of(),0,0,false)
                && player.level()==bedroom && DreamStore.load(player).inBedroom,
                "invalid effect also cancels generic exit");
    }
    private Path temporary() {
        return snapshotPath().resolveSibling(snapshotPath().getFileName() + ".tmp");
    }
    private void failedSave() throws Exception {
        wake();
        byte[] original = Files.readAllBytes(snapshotPath());
        Files.createDirectory(temporary());
        try {
            MTDream.enter(player, bedroomBed);
            check(player.level()==bedroom && player.gameMode()==GameType.ADVENTURE
                    && !player.hasEffect(MobEffects.SPEED)
                    && Arrays.equals(original, Files.readAllBytes(snapshotPath())),
                    "save failure cancels bed return and preserves the original record");
            check(!player.teleportTo(server.overworld(),40.5,200,40.5,Set.of(),0,0,false),
                    "save failure also cancels generic exit");
        } finally { Files.delete(temporary()); }
        MTDream.enter(player, bedroomBed);
        check(player.level()==server.overworld() && player.hasEffect(MobEffects.SPEED)
                && !DreamStore.load(player).inBedroom, "return succeeds after write failure is removed");
        player.setHealth(6);
        player.getFoodData().setFoodLevel(3);
        player.removeAllEffects();
        move(bedroom,4239.5,66,799.5);
        MTDream.enter(player, bedroomBed);
        check(player.getHealth()==6 && player.getFoodData().getFoodLevel()==3
                && !player.hasEffect(MobEffects.SPEED), "successful retry cannot be consumed twice");
    }
    private void portalExit() throws Exception {
        wake();
        move(bedroom,4240.5,66,799.5);
        check(DreamStore.load(player).inBedroom && !player.hasEffect(MobEffects.SPEED),
                "same-bedroom travel leaves the snapshot suspended");
        var destination = server.getLevel(Level.NETHER);
        var result = player.teleport(new TeleportTransition(destination,
                new Vec3(40.5,200,40.5),Vec3.ZERO,0,0,TeleportTransition.DO_NOTHING));
        check(result==player && player.level()==destination && player.hasEffect(MobEffects.SPEED)
                && !DreamStore.load(player).inBedroom && player.gameMode()==GameType.SURVIVAL,
                "direct portal transition to another dimension also settles snapshot");
    }
    private void failedFinalSave() throws Exception {
        if (!listenerRegistered) {
            listenerRegistered = true;
            ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((traveler, origin, destination) -> {
                if (!traveler.getUUID().equals(failAfterExit)) return;
                failAfterExit = null;
                Path path = traveler.level().getServer().getWorldPath(LevelResource.ROOT)
                        .resolve("mirrortree/dreams/" + traveler.getUUID() + ".json.tmp");
                try { Files.createDirectory(path); }
                catch (Exception error) { throw new RuntimeException(error); }
            });
        }
        wake();
        failAfterExit = player.getUUID();
        try {
            move(server.overworld(),40.5,200,40.5);
            var disk = com.google.gson.JsonParser.parseString(Files.readString(snapshotPath())).getAsJsonObject();
            check(!disk.get("inBedroom").getAsBoolean() && disk.get("returning").getAsBoolean()
                    && player.hasEffect(MobEffects.SPEED), "final write failure leaves durable consumed intent");
            player.setHealth(6);
            player.removeAllEffects();
            check(!player.teleportTo(bedroom,4239.5,66,799.5,Set.of(),0,0,false)
                    && player.level()==server.overworld() && player.getHealth()==6,
                    "unsettled commit blocks re-entry without replaying state");
        } finally {
            failAfterExit = null;
            Files.deleteIfExists(temporary());
        }
        move(bedroom,4239.5,66,799.5);
        MTDream.enter(player, bedroomBed);
        check(player.getHealth()==6 && !player.hasEffect(MobEffects.SPEED)
                && !DreamStore.load(player).inBedroom, "pending commit recovers before re-entry without replay");
    }
    private void rejectedTeleport() throws Exception {
        wake();
        check(MTDream.teleport(player,server.overworld(),() -> null)==null
                && DreamStore.load(player).inBedroom && !player.hasEffect(MobEffects.SPEED),
                "rejected underlying teleport keeps original snapshot and suspended state");
        try {
            MTDream.teleport(player,server.overworld(),() -> {
                try { Files.createDirectory(temporary()); }
                catch (Exception error) { throw new RuntimeException(error); }
                return null;
            });
        } finally { Files.deleteIfExists(temporary()); }
        check(DreamStore.load(player).inBedroom && !player.hasEffect(MobEffects.SPEED),
                "rejected teleport with failed rollback recovers the active snapshot");
        MTDream.enter(player, bedroomBed);
        check(player.level()==server.overworld() && player.hasEffect(MobEffects.SPEED)
                && !DreamStore.load(player).inBedroom, "recovered snapshot remains usable exactly once");
    }
    private void deathCounter() throws Exception {
        wake();
        var saved = DreamStore.load(player);
        check(saved.deaths != null, "new snapshot records the death counter");
        player.awardStat(net.minecraft.stats.Stats.DEATHS);
        check(!DreamStore.load(player).inBedroom && !DreamStore.load(player).hasVitals,
                "death counter invalidates stale disk state even without a respawn callback");
    }
    private void deathSnapshot(boolean failWrite) throws Exception {
        wake();
        // Legacy records must also be discarded by the respawn callback.
        if (failWrite) {
            var legacy = DreamStore.load(player);
            legacy.deaths = null;
            DreamStore.save(player, legacy);
            Files.createDirectory(temporary());
        }
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(
            LevelData.RespawnData.of(Level.OVERWORLD,new BlockPos(40,200,40),0,0),true),false);
        player.hasChangedDimension();
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.hurtServer(bedroom,player.damageSources().genericKill(),Float.MAX_VALUE);
        check(player.isDeadOrDying(), "snapshot fixture dies in bedroom");
        player.connection.handleClientCommand(new ServerboundClientCommandPacket(
            ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        player = player.connection.player;
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        check(player.level()==server.overworld() && !player.hasEffect(MobEffects.SPEED),
                "death respawn does not restore suspended effects");
        if (failWrite) {
            try {
                DreamStore.load(player);
                check(false, "failed invalidation must block stale snapshot reads");
            } catch (java.io.IOException expected) {
                check(true, "failed invalidation blocks stale legacy snapshot reads");
            } finally { Files.delete(temporary()); }
        }
        check(!DreamStore.load(player).inBedroom && !DreamStore.load(player).hasVitals,
                "death consumes the snapshot, including after a failed write retry");
        player.setHealth(6);
        player.getFoodData().setFoodLevel(3);
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,100));
        move(bedroom,4239.5,66,799.5);
        MTDream.enter(player, bedroomBed);
        check(player.getHealth()==6 && player.getFoodData().getFoodLevel()==3
                && player.hasEffect(MobEffects.SLOWNESS) && !player.hasEffect(MobEffects.SPEED),
                "later bed use cannot replay pre-death state");
    }
    private void respawn() throws Exception {
        move(server.overworld(),40.5,200,40.5);
        player.setGameMode(GameType.SURVIVAL);
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(
            LevelData.RespawnData.of(Level.OVERWORLD,new BlockPos(40,200,40),0,0),true),false);
        move(bedroom,4239.5,66,799.5);
        player.hasChangedDimension();
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.hurtServer(bedroom,player.damageSources().genericKill(),Float.MAX_VALUE);
        check(player.isDeadOrDying(), "fixture player did not die");
        player.connection.handleClientCommand(new ServerboundClientCommandPacket(
            ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        player = player.connection.player;
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        var tick = MTDream.class.getDeclaredMethod("tick",MinecraftServer.class);
        tick.setAccessible(true);
        tick.invoke(null,server);
        check(player.level()==server.overworld() && player.gameMode()==GameType.SURVIVAL,
                "vanilla death and respawn still restore survival outside bedroom");
    }
    private static void platform(ServerLevel level, BlockPos position) {
        for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++) {
            level.setBlockAndUpdate(position.offset(x,-1,z),Blocks.STONE.defaultBlockState());
            for(int y=0;y<3;y++) level.setBlockAndUpdate(position.offset(x,y,z),Blocks.AIR.defaultBlockState());
        }
    }
}
