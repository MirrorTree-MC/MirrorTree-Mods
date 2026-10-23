package top.bearcabbage.lanterninstorm.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;

@Mixin(Minecraft.class)
public abstract class MinecraftControlsMixin {
  @Unique private boolean storm$autoMining;

  @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
  private void storm$keepMining(boolean down, CallbackInfo ci) {
    var p = ((Minecraft) (Object) this).player;
    if (p != null && p.hasEffect(AnnoyingEffects.CONTROLS_ALWAYS_DIG) && !storm$autoMining)
      ci.cancel();
  }

  @Shadow
  private void continueAttack(boolean down) {}

  @Shadow
  private void startUseItem() {}

  @Inject(method = "tick", at = @At("RETURN"))
  private void storm$automaticActions(CallbackInfo ci) {
    var client = (Minecraft) (Object) this;
    var player = client.player;
    if (player == null || client.screen != null || client.isPaused()) return;
    if (player.hasEffect(AnnoyingEffects.CONTROLS_ALWAYS_DIG)) {
      client.missTime = 0;
      storm$autoMining = true;
      try {
        continueAttack(true);
      } finally {
        storm$autoMining = false;
      }
    }
    var use = player.getEffect(AnnoyingEffects.CONTROLS_CHAOTIC_USE);
    if (use != null && player.getRandom().nextInt(50) <= use.getAmplifier()) startUseItem();
  }
}
