package top.bearcabbage.mirrorskills.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.world.effect.MobEffectInstance;

/** Preserves vanilla's format and the markers on every layer of its hidden-effect chain. */
public record MarkedEffectCodec(Codec<MobEffectInstance> delegate) implements Codec<MobEffectInstance> {
    private static final String DURATION = "mirrorskills.durationModified";
    private static final String LEVEL = "mirrorskills.levelModified";
    private static final String HIDDEN = "hidden_effect";

    @Override
    public <T> DataResult<Pair<MobEffectInstance, T>> decode(DynamicOps<T> ops, T input) {
        return delegate.decode(ops, input).map(pair -> {
            readMarkers(pair.getFirst(), ops, input);
            return pair;
        });
    }

    private static <T> void readMarkers(MobEffectInstance effect, DynamicOps<T> ops, T input) {
        var flags = (StatusEffectInstanceExtensions) effect;
        flags.mirrorskills$setDurationModified(ops.get(input, DURATION).flatMap(ops::getBooleanValue).result().orElse(false));
        flags.mirrorskills$setLevelModified(ops.get(input, LEVEL).flatMap(ops::getBooleanValue).result().orElse(false));
        var hidden = flags.mirrorskills$getHiddenEffect();
        if (hidden != null) ops.get(input, HIDDEN).result().ifPresent(child -> readMarkers(hidden, ops, child));
    }

    @Override
    public <T> DataResult<T> encode(MobEffectInstance effect, DynamicOps<T> ops, T prefix) {
        return delegate.encode(effect, ops, prefix).flatMap(value -> writeMarkers(effect, ops, value));
    }

    private static <T> DataResult<T> writeMarkers(MobEffectInstance effect, DynamicOps<T> ops, T input) {
        var flags = (StatusEffectInstanceExtensions) effect;
        var result = DataResult.success(ops.remove(ops.remove(input, DURATION), LEVEL));
        if (flags.mirrorskills$isDurationModified())
            result = result.flatMap(value -> ops.mergeToMap(value, ops.createString(DURATION), ops.createBoolean(true)));
        if (flags.mirrorskills$isLevelModified())
            result = result.flatMap(value -> ops.mergeToMap(value, ops.createString(LEVEL), ops.createBoolean(true)));
        var hidden = flags.mirrorskills$getHiddenEffect();
        // Vanilla encodes nested layers through Details, bypassing this outer Codec.
        if (hidden != null) {
            result = result.flatMap(value -> ops.get(value, HIDDEN)
                    .flatMap(child -> writeMarkers(hidden, ops, child))
                    .flatMap(child -> ops.mergeToMap(ops.remove(value, HIDDEN), ops.createString(HIDDEN), child)));
        }
        return result;
    }
}
