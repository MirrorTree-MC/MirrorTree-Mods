package top.bearcabbage.lanterninstorm.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;

@Mixin(EntityRenderer.class)
public abstract class EntityVisibilityMixin {
  @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
  private void storm$hideCreeper(
      Entity entity,
      Frustum frustum,
      double x,
      double y,
      double z,
      CallbackInfoReturnable<Boolean> cir) {
    var player = Minecraft.getInstance().player;
    if (entity instanceof Creeper
        && player != null
        && player.hasEffect(AnnoyingEffects.CREEPERPHOBIA)) cir.setReturnValue(false);
  }
}
