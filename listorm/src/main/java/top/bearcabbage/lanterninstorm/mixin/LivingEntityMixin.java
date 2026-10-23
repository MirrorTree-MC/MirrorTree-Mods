package top.bearcabbage.lanterninstorm.mixin;

import java.util.ArrayDeque;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bearcabbage.annoyingeffects.StatusEffectInstanceStackHolder;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements StatusEffectInstanceStackHolder {
  @com.llamalad7.mixinextras.injector.ModifyExpressionValue(
      method = "travelInAir",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
  private float storm$slip(float friction) {
    return ((LivingEntity) (Object) this)
            .hasEffect(top.bearcabbage.annoyingeffects.AnnoyingEffects.SLIPPY)
        ? .98F
        : friction;
  }

  @Unique private final ArrayDeque<MobEffectInstance> storm$pending = new ArrayDeque<>();

  @Override
  public void pushStatusEffect(MobEffectInstance effect) {
    storm$pending.addLast(effect);
  }

  @Override
  public MobEffectInstance popStatusEffect() {
    return storm$pending.pollFirst();
  }

  @Inject(method = "tick", at = @At("TAIL"))
  private void storm$drain(CallbackInfo ci) {
    LivingEntity self = (LivingEntity) (Object) this;
    if (!self.level().isClientSide())
      while (!storm$pending.isEmpty()) self.addEffect(storm$pending.removeFirst());
  }
}
