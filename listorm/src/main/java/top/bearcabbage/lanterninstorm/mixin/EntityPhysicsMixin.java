package top.bearcabbage.lanterninstorm.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;

@Mixin(Entity.class)
public abstract class EntityPhysicsMixin {
  @Unique private double storm$verticalSpeed;

  @ModifyArg(
      method = "updateFluidInteraction",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/EntityFluidInteraction;applyCurrentTo(Lnet/minecraft/tags/TagKey;Lnet/minecraft/world/entity/Entity;D)V",
              ordinal = 0),
      index = 2)
  private double storm$heavyWaterCurrent(double scale) {
    return (Object) this instanceof LivingEntity entity
            && entity.hasEffect(AnnoyingEffects.HEAVINESS)
        ? scale / 2
        : scale;
  }

  @Inject(method = "baseTick", at = @At("HEAD"))
  private void storm$remember(CallbackInfo ci) {
    storm$verticalSpeed = ((Entity) (Object) this).getDeltaMovement().y();
  }

  @Inject(method = "baseTick", at = @At("RETURN"))
  private void storm$gravity(CallbackInfo ci) {
    if ((Object) this instanceof LivingEntity entity) {
      if (entity.hasEffect(AnnoyingEffects.HEAVINESS))
        entity.setDeltaMovement(entity.getDeltaMovement().add(0, -0.15, 0));
      else if (entity.hasEffect(AnnoyingEffects.WATER_FILLING))
        entity.setDeltaMovement(entity.getDeltaMovement().add(0, -0.03, 0));
    }
  }

  @Inject(
      method = {"resetFallDistance", "checkFallDistanceAccumulation"},
      at = @At("HEAD"),
      cancellable = true)
  private void storm$waterFall(CallbackInfo ci) {
    if ((Object) this instanceof LivingEntity entity
        && entity.hasEffect(AnnoyingEffects.HEAVINESS)) {
      entity.fallDistance =
          Math.min(entity.fallDistance, 2 * storm$verticalSpeed * storm$verticalSpeed);
      ci.cancel();
    }
  }

  @Inject(method = "checkFallDamage", at = @At("RETURN"))
  private void storm$landed(
      double y, boolean onGround, BlockState state, BlockPos pos, CallbackInfo ci) {
    if (onGround
        && (Object) this instanceof LivingEntity entity
        && entity.hasEffect(AnnoyingEffects.HEAVINESS)) entity.fallDistance = 0;
  }
}
