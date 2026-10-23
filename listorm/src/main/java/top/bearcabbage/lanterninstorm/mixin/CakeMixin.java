package top.bearcabbage.lanterninstorm.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bearcabbage.lanterninstorm.progression.ProgressionService;

@Mixin(CakeBlock.class)
public abstract class CakeMixin {
  @Inject(method = "eat", at = @At("RETURN"))
  private static void storm$cake(
      LevelAccessor level,
      BlockPos pos,
      BlockState state,
      Player player,
      CallbackInfoReturnable<InteractionResult> cir) {
    if (player instanceof ServerPlayer serverPlayer && cir.getReturnValue().consumesAction())
      ProgressionService.award(serverPlayer, "food", "minecraft:cake", 1);
  }
}
