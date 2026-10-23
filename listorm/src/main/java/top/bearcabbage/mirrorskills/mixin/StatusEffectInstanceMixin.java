package top.bearcabbage.mirrorskills.mixin;
import com.mojang.serialization.Codec;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bearcabbage.mirrorskills.util.StatusEffectInstanceExtensions;
import top.bearcabbage.mirrorskills.util.MarkedEffectCodec;
@Mixin(MobEffectInstance.class)
public class StatusEffectInstanceMixin implements StatusEffectInstanceExtensions {
 @Shadow @Final @Mutable public static Codec<MobEffectInstance> CODEC;
 @Unique private boolean mirrorskills$durationModified;
 @Unique private boolean mirrorskills$levelModified;
 public boolean mirrorskills$isDurationModified(){return mirrorskills$durationModified;}
 public boolean mirrorskills$isLevelModified(){return mirrorskills$levelModified;}
 public void mirrorskills$setDurationModified(boolean v){mirrorskills$durationModified=v;}
 public void mirrorskills$setLevelModified(boolean v){mirrorskills$levelModified=v;}
 @Inject(method="<clinit>",at=@At("TAIL"))
 private static void mirrorskills$persistMarkers(CallbackInfo ci){CODEC=new MarkedEffectCodec(CODEC);}
 @Inject(method="<init>(Lnet/minecraft/world/effect/MobEffectInstance;)V",at=@At("RETURN"))
 private void mirrorskills$copy(MobEffectInstance other,CallbackInfo ci){
  var flags=(StatusEffectInstanceExtensions)other;
  mirrorskills$durationModified=flags.mirrorskills$isDurationModified();
  mirrorskills$levelModified=flags.mirrorskills$isLevelModified();
 }
}
