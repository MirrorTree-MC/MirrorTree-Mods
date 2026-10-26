package top.bearcabbage.lanterninstorm.mixin;

import java.util.ArrayDeque;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bearcabbage.annoyingeffects.StatusEffectInstanceStackHolder;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements StatusEffectInstanceStackHolder {
  @Inject(method = "baseTick", at = @At("HEAD"))
  private void storm$bedroomSafety(CallbackInfo ci) {
    // Clear before vanilla creates its effect iterator or applies effect-driven physics.
    if ((Object) this instanceof net.minecraft.server.level.ServerPlayer player)
      top.bearcabbage.lanterninstorm.safety.SafetyService.protectBedroom(player);
  }

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
  @Unique private final ArrayDeque<Holder<MobEffect>> storm$removals = new ArrayDeque<>();

  @Override
  public void pushStatusEffect(MobEffectInstance effect) {
    storm$pending.addLast(effect);
  }

  @Override
  public MobEffectInstance popStatusEffect() {
    return storm$pending.pollFirst();
  }

  @Override
  public void queueStatusEffectRemoval(Holder<MobEffect> effect) {
    storm$removals.addLast(effect);
  }

  @Inject(method = "tick", at = @At("TAIL"))
  private void storm$drain(CallbackInfo ci) {
    LivingEntity self = (LivingEntity) (Object) this;
    if (!self.level().isClientSide()) {
      while (!storm$removals.isEmpty()) self.removeEffect(storm$removals.removeFirst());
      while (!storm$pending.isEmpty()) self.addEffect(storm$pending.removeFirst());
    }
  }
}
