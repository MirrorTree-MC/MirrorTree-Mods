package top.bearcabbage.lanterninstorm.client.mixin;

import net.minecraft.client.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;
import top.bearcabbage.annoyingeffects.effect.SchizophreniaStatusEffect;

@Mixin(Camera.class)
public abstract class CameraMixin {
  @Shadow
  public abstract Vec3 position();

  @Shadow
  protected abstract void setPosition(Vec3 position);

  @Inject(method = "alignWithEntity", at = @At("RETURN"))
  private void storm$offset(float partialTicks, CallbackInfo ci) {
    var p = Minecraft.getInstance().player;
    if (p != null && p.hasEffect(AnnoyingEffects.SCHIZOPHRENIA))
      setPosition(position().add(SchizophreniaStatusEffect.OFFSET_X, 0, 0));
  }
}
