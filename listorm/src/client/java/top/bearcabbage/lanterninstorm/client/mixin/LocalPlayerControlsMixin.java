package top.bearcabbage.lanterninstorm.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerControlsMixin {
  @Inject(method = "applyInput", at = @At("RETURN"))
  private void storm$input(CallbackInfo ci) {
    var player = (LocalPlayer) (Object) this;
    if (player.hasEffect(AnnoyingEffects.CONTROLS_CRAB)) player.zza = 0;
    if (player.hasEffect(AnnoyingEffects.CONTROLS_MIRROR)) player.xxa *= -1;
    if (player.hasEffect(AnnoyingEffects.CONTROLS_ALWAYS_JUMP)) player.setJumping(true);
    if (player.hasEffect(AnnoyingEffects.REALLY_COLD)) player.setJumping(false);
  }
}
