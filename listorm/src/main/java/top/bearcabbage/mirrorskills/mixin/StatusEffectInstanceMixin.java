package top.bearcabbage.mirrorskills.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.world.effect.MobEffectInstance;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bearcabbage.mirrorskills.util.MarkedEffectCodec;
import top.bearcabbage.mirrorskills.util.StatusEffectInstanceExtensions;

@Mixin(MobEffectInstance.class)
public class StatusEffectInstanceMixin implements StatusEffectInstanceExtensions {
    @Shadow @Final @Mutable public static Codec<MobEffectInstance> CODEC;
    @Shadow private MobEffectInstance hiddenEffect;
    @Unique private boolean mirrorskills$durationModified;
    @Unique private boolean mirrorskills$levelModified;

    public MobEffectInstance mirrorskills$getHiddenEffect() { return hiddenEffect; }
    public boolean mirrorskills$isDurationModified() { return mirrorskills$durationModified; }
    public boolean mirrorskills$isLevelModified() { return mirrorskills$levelModified; }
    public void mirrorskills$setDurationModified(boolean value) { mirrorskills$durationModified = value; }
    public void mirrorskills$setLevelModified(boolean value) { mirrorskills$levelModified = value; }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void mirrorskills$persistMarkers(CallbackInfo ci) {
        CODEC = new MarkedEffectCodec(CODEC);
    }

    // Both vanilla's copy constructor and hidden-effect promotion use this method.
    @Inject(method = "setDetailsFrom", at = @At("RETURN"))
    private void mirrorskills$copyMarkers(MobEffectInstance other, CallbackInfo ci) {
        var flags = (StatusEffectInstanceExtensions) other;
        mirrorskills$durationModified = flags.mirrorskills$isDurationModified();
        mirrorskills$levelModified = flags.mirrorskills$isLevelModified();
    }

    // Follow actual field assignments: an extension may replace duration without replacing level.
    // These injections run after vanilla has preserved the old effect in its hidden chain.
    @Inject(method = "update", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/effect/MobEffectInstance;duration:I",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER), require = 2, allow = 2)
    private void mirrorskills$adoptDurationMarker(MobEffectInstance other, CallbackInfoReturnable<Boolean> cir) {
        mirrorskills$durationModified = ((StatusEffectInstanceExtensions) other).mirrorskills$isDurationModified();
    }

    @Inject(method = "update", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/effect/MobEffectInstance;amplifier:I",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER), require = 1, allow = 1)
    private void mirrorskills$adoptLevelMarker(MobEffectInstance other, CallbackInfoReturnable<Boolean> cir) {
        mirrorskills$levelModified = ((StatusEffectInstanceExtensions) other).mirrorskills$isLevelModified();
    }
}
