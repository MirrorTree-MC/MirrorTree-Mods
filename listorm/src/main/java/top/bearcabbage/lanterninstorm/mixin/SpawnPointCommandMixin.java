package top.bearcabbage.lanterninstorm.mixin;

import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.server.commands.SetSpawnCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bearcabbage.lanterninstorm.LanternInStormAPI;

@Mixin(SetSpawnCommand.class)
public abstract class SpawnPointCommandMixin {
  @Inject(method = "setSpawn", at = @At("RETURN"))
  private static void storm$spawnpoint(
      CommandSourceStack source,
      Collection<ServerPlayer> targets,
      BlockPos pos,
      Coordinates rotation,
      CallbackInfoReturnable<Integer> cir) {
    if (LanternInStormAPI.setRTPSpawnWhenSpawnpointCommand)
      targets.forEach(p -> LanternInStormAPI.setRTPSpawn(p, pos, false));
  }
}
