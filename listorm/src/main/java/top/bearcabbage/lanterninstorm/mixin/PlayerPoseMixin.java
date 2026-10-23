package top.bearcabbage.lanterninstorm.mixin;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;

@Mixin(Player.class)
public abstract class PlayerPoseMixin {
  @Inject(method = "canPlayerFitWithinBlocksAndEntitiesWhen", at = @At("HEAD"), cancellable = true)
  private void storm$crawl(Pose pose, CallbackInfoReturnable<Boolean> cir) {
    if (((Player) (Object) this).hasEffect(AnnoyingEffects.CRAWLER)) cir.setReturnValue(false);
  }
}
