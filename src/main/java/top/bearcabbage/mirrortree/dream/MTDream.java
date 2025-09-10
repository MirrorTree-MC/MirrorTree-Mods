package top.bearcabbage.mirrortree.dream;

import com.google.common.collect.Maps;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Position;
import net.minecraft.world.GameMode;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import static top.bearcabbage.mirrortree.MirrorTree.*;

public class MTDream {
    public static final int MAX_RANGE = 3000;
    private static final int DREAM_RANDOM_RANGE = 128;
    public static BlockPos pos;
    public static long lastTime = 0;

    private static final LinkedBlockingQueue<Runnable> dreamQueue = new LinkedBlockingQueue<>();
    private static final LinkedBlockingQueue<Runnable> redreamQueue = new LinkedBlockingQueue<>();
    private static final ExecutorService dreamExecutor = Executors.newSingleThreadExecutor();
    private static final ExecutorService redreamExecutor = Executors.newSingleThreadExecutor();


    public static void queueDreamingTask(ServerWorld world, ServerPlayerEntity player, Position warppos) {
        dreamQueue.add(() -> dreaming(world, player, warppos));
        processDreamQueue();
    }

    private static void processDreamQueue() {
        dreamExecutor.submit(() -> {
            try {
                while (!dreamQueue.isEmpty()) {
                    Runnable task = dreamQueue.take();
                    task.run();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void queueRedreamingTask(ServerWorld world, ServerPlayerEntity player) {
        redreamQueue.add(() -> redreaming(world, player));
        processRedreamQueue();
    }

    private static void processRedreamQueue() {
        redreamExecutor.submit(() -> {
            try {
                while (!redreamQueue.isEmpty()) {
                    Runnable task = redreamQueue.take();
                    task.run();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }


    // 正常关服时写文件储存数据
    public static final Map<UUID, double[]> dreamingPos = Maps.newHashMap();
    // 这个不写入
    public static final Map<UUID, Collection<StatusEffectInstance>> dreamingEffects = Maps.newHashMap();
    public static final Map<UUID, ArrayList<Float>> dreamingHealthAndHunger = Maps.newHashMap();

    public static void dreaming(ServerWorld world, ServerPlayerEntity player, Position warppos) {
        lastTime = System.currentTimeMillis();
        if (dreamingPos.containsKey(player.getUuid())) {
            player.getServer().execute(() -> {((ServerPlayerEntity) player).teleport(world.getServer().getOverworld(), dreamingPos.get(player.getUuid())[0]+0.5, dreamingPos.get(player.getUuid())[1]+0.5, dreamingPos.get(player.getUuid())[2]+0.5, 0, 0);});
            dreamingPos.remove(player.getUuid());
        } else {
            if (warppos == null) {
                lastTime = System.currentTimeMillis();
                pos = getRandomPos(0, 0, MAX_RANGE);
                player.getServer().execute(() -> {
                    player.teleport(world, pos.toCenterPos().getX(), pos.toCenterPos().getY(), pos.toCenterPos().getZ(), 0, 0);
                    player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("大鹏的梦").withColor(0x525288)));
                    player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("这是你的起点").withColor(0xFFFFFF)));
                    player.setSpawnPoint(world.getRegistryKey(), BlockPos.ofFloored(pos.toCenterPos()), 0, true, false);
                });
                } else {
                player.getServer().execute(() -> {
                    player.teleport(world, warppos.getX(), warppos.getY(), warppos.getZ(), 0, 0);
                    player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("大鹏的梦").withColor(0x525288)));
                    player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("这个「聚落」是你的起点").withColor(0xFFFFFF)));
                    player.setSpawnPoint(world.getRegistryKey(), BlockPos.ofFloored(warppos), 0, true, false);
                });
            }
            player.changeGameMode(GameMode.SURVIVAL);
        }
        if (dreamingEffects.containsKey(player.getUuid())) {
            for (StatusEffectInstance effect : dreamingEffects.get(player.getUuid())) {
                player.addStatusEffect(effect);
            }
            dreamingEffects.remove(player.getUuid());
        }
        if (dreamingHealthAndHunger.containsKey(player.getUuid())) {
            ArrayList<Float> healthAndHunger = dreamingHealthAndHunger.get(player.getUuid());
            player.setHealth(healthAndHunger.get(0));
            player.getHungerManager().setFoodLevel((int) Math.floor(healthAndHunger.get(1)));
            player.getHungerManager().setSaturationLevel(healthAndHunger.get(2));
            player.getHungerManager().setExhaustion(healthAndHunger.get(3));
            dreamingHealthAndHunger.remove(player.getUuid());
        }
    }

    public static void redreaming(ServerWorld world, ServerPlayerEntity player) {
        dreamingPos.remove(player.getUuid());
        dreamingEffects.remove(player.getUuid());
        dreamingHealthAndHunger.remove(player.getUuid());
        player.getServer().execute(() -> {player.teleport(world.getServer().getWorld(bedroom), bedroomX_init, bedroomY_init, bedroomZ_init, 90,0);});
        player.clearStatusEffects();
        player.changeGameMode(GameMode.ADVENTURE);
        player.setSpawnPoint(bedroom, new BlockPos(bedroomX_init, bedroomY_init, bedroomZ_init), 90, true, false);
    }

    private static BlockPos getRandomPos(int xx, int zz, int range) {
        return (xx==0 && zz==0) ? MTDreamingPoint.getRandomPos() : MTDreamingPoint.getNearbyRandomPos(xx, zz, range);
    }
}