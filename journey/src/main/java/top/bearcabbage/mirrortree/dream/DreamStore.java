package top.bearcabbage.mirrortree.dream;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.storage.LevelResource;
import top.bearcabbage.mirrortree.MirrorTree;

/** World-scoped snapshots, written before leaving a dream and after a successful return. */
final class DreamStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final java.util.Set<Path> INVALIDATED = new java.util.HashSet<>();

    static final class Snapshot {
        boolean inBedroom;
        // Durable intent: resolve against the player's dimension before any later bedroom crossing.
        boolean returning;
        Integer deaths;
        String dimension = "minecraft:overworld";
        double x, y, z;
        float yaw, pitch, health, saturation, exhaustion;
        Float absorption;
        int food;
        boolean hasVitals;
        JsonArray effects = new JsonArray();

        static Snapshot capture(ServerPlayer player) {
            Snapshot s = new Snapshot();
            s.inBedroom = true;
            s.deaths = player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
            s.dimension = player.level().dimension().identifier().toString();
            s.x = player.getX(); s.y = player.getY(); s.z = player.getZ();
            s.yaw = player.getYRot(); s.pitch = player.getXRot();
            s.health = player.getHealth();
            s.absorption = player.getAbsorptionAmount();
            s.food = player.getFoodData().getFoodLevel();
            s.saturation = player.getFoodData().getSaturationLevel();
            s.exhaustion = ((top.bearcabbage.mirrortree.mixin.FoodDataAccessor)player.getFoodData()).mirrortree$getExhaustion();
            s.hasVitals = true;
            var ops = player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            // Encoding on the server thread creates a detached snapshot of the complete hidden chain.
            // Vanilla's copy constructor copies only the active layer.
            for (var effect : player.getActiveEffects())
                s.effects.add(MobEffectInstance.CODEC.encodeStart(ops, effect).getOrThrow());
            return s;
        }

        Runnable prepareRestore(ServerPlayer player) {
            if (!hasVitals) return () -> {};
            if (!Float.isFinite(health) || health <= 0 || food < 0 || food > 20
                    || !Float.isFinite(saturation) || saturation < 0
                    || !Float.isFinite(exhaustion) || exhaustion < 0
                    || (absorption != null && (!Float.isFinite(absorption) || absorption < 0)))
                throw new IllegalArgumentException("Invalid dream vitals");
            // Decode first so a bad snapshot cannot partially clear the current effects.
            var ops = player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var decoded = new java.util.ArrayList<MobEffectInstance>();
            for (var json : effects) decoded.add(MobEffectInstance.CODEC.parse(ops, json).getOrThrow());
            return () -> {
                player.removeAllEffects();
                for (var effect : decoded) player.addEffect(effect);
                if (absorption != null) player.setAbsorptionAmount(absorption);
                player.setHealth(Math.min(health, player.getMaxHealth()));
                player.getFoodData().setFoodLevel(food);
                player.getFoodData().setSaturation(saturation);
                ((top.bearcabbage.mirrortree.mixin.FoodDataAccessor)player.getFoodData()).mirrortree$setExhaustion(exhaustion);
            };
        }
    }

    private static Path path(ServerPlayer player) {
        return player.level().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("mirrortree/dreams").resolve(player.getUUID() + ".json");
    }

    static Snapshot load(ServerPlayer player) throws IOException {
        Path file = path(player);
        if (INVALIDATED.contains(file)) {
            discard(player);
            return new Snapshot();
        }
        if (Files.exists(file)) {
            Snapshot s = GSON.fromJson(Files.readString(file), Snapshot.class);
            if (s == null || !Double.isFinite(s.x) || !Double.isFinite(s.y) || !Double.isFinite(s.z)
                    || !Float.isFinite(s.yaw) || !Float.isFinite(s.pitch))
                throw new IOException("Invalid dream snapshot: " + file);
            if ((s.inBedroom || s.returning) && s.deaths != null
                    && s.deaths != player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS))) {
                discard(player);
                return new Snapshot();
            }
            if (s.returning) {
                s.inBedroom = player.level().dimension().equals(MirrorTree.bedroom);
                s.returning = false;
                save(player, s);
            }
            return s;
        }
        // The server branch used config/mirrortree/dream_data.json with UUID -> [x,y,z].
        double[] old = MirrorTree.dream.get(player.getUUID().toString(), double[].class);
        if (old != null && old.length == 3) {
            Snapshot s = new Snapshot();
            s.inBedroom = true; s.x = old[0] + .5; s.y = old[1] + .5; s.z = old[2] + .5;
            return s;
        }
        return new Snapshot();
    }

    static void discard(ServerPlayer player) throws IOException {
        Path file = path(player);
        // Keep failed invalidations blocked until a blank record is durably written.
        INVALIDATED.add(file);
        save(player, new Snapshot());
        INVALIDATED.remove(file);
    }

    static void save(ServerPlayer player, Snapshot snapshot) throws IOException {
        Path file = path(player), temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.createDirectories(file.getParent());
        Files.writeString(temp, GSON.toJson(snapshot));
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
