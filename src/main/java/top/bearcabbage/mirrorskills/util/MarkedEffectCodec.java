package top.bearcabbage.mirrorskills.util;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import net.minecraft.world.effect.MobEffectInstance;
/** Preserves vanilla's format and stores flags to prevent repeated bonuses. */
public record MarkedEffectCodec(Codec<MobEffectInstance> delegate) implements Codec<MobEffectInstance> {
 public <T> DataResult<Pair<MobEffectInstance,T>> decode(DynamicOps<T> ops,T input){
  return delegate.decode(ops,input).map(pair->{
   var flags=(StatusEffectInstanceExtensions)pair.getFirst();
   flags.mirrorskills$setDurationModified(ops.get(input,"mirrorskills.durationModified").flatMap(ops::getBooleanValue).result().orElse(false));
   flags.mirrorskills$setLevelModified(ops.get(input,"mirrorskills.levelModified").flatMap(ops::getBooleanValue).result().orElse(false));
   return pair;
  });
 }
 public <T> DataResult<T> encode(MobEffectInstance effect,DynamicOps<T> ops,T prefix){
  var flags=(StatusEffectInstanceExtensions)effect;
  var result=delegate.encode(effect,ops,prefix);
  if(flags.mirrorskills$isDurationModified())result=result.flatMap(v->ops.mergeToMap(v,ops.createString("mirrorskills.durationModified"),ops.createBoolean(true)));
  if(flags.mirrorskills$isLevelModified())result=result.flatMap(v->ops.mergeToMap(v,ops.createString("mirrorskills.levelModified"),ops.createBoolean(true)));
  return result;
 }
}
