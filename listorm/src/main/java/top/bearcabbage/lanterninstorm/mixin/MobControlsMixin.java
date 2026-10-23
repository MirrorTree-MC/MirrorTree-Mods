package top.bearcabbage.lanterninstorm.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;

@Mixin(Mob.class)
public abstract class MobControlsMixin {
  @Inject(method = "serverAiStep", at = @At("RETURN"))
  private void storm$controls(CallbackInfo ci) {
    var self = (Mob) (Object) this;
    if (self.hasEffect(AnnoyingEffects.CONTROLS_CRAB)) self.zza = 0;
    if (self.hasEffect(AnnoyingEffects.CONTROLS_MIRROR)) self.xxa *= -1;
    if (self.hasEffect(AnnoyingEffects.CONTROLS_ALWAYS_JUMP)) self.setJumping(true);
    if (self.hasEffect(AnnoyingEffects.REALLY_COLD)) self.setJumping(false);
  }
}
