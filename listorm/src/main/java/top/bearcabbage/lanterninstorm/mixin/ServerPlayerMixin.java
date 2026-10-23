package top.bearcabbage.lanterninstorm.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import top.bearcabbage.lanterninstorm.effects.EffectRuntime;
import top.bearcabbage.lanterninstorm.progression.PlayerStore;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
  @Inject(method = "die", at = @At("HEAD"))
  private void storm$vanish(DamageSource source, CallbackInfo ci) {
    EffectRuntime.vanish((ServerPlayer) (Object) this);
  }

  @Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("RETURN"), cancellable = true)
  private void storm$respawn(
      boolean consume,
      TeleportTransition.PostTeleportTransition post,
      CallbackInfoReturnable<TeleportTransition> cir) {
    ServerPlayer player = (ServerPlayer) (Object) this;
    if (player.getRespawnConfig() != null && !cir.getReturnValue().missingRespawnBlock()) return;
    var state = PlayerStore.get(player);
    if (state.spawn == null) return;
    var level =
        player
            .level()
            .getServer()
            .getLevel(
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(state.spawnDimension)));
    if (level != null)
      cir.setReturnValue(
          new TeleportTransition(
              level,
              Vec3.atCenterOf(new BlockPos(state.spawn[0], state.spawn[1], state.spawn[2])),
              Vec3.ZERO,
              0,
              0,
              post));
  }
}
