package top.bearcabbage.annoyingeffects.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoulSandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import top.bearcabbage.annoyingeffects.StatusEffectInstanceStackHolder;
import top.bearcabbage.annoyingeffects.utils.NoSavePlayerData;

public class WaterFillingStatusEffect extends AnnoyingStatusEffect {
  public final double REACH = 3F;
  public final double BREAKING_FORCE = 2.5F;

  public static final NoSavePlayerData<Integer> WaterTicks = new NoSavePlayerData<>(0);

  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return true;
  }

  @Override
  public boolean applyEffectTick(
      net.minecraft.server.level.ServerLevel serverLevel, LivingEntity entity, int amplifier) {
    FlowingFluid fluid;
    Block blockFluid;
    if (serverLevel
        .environmentAttributes()
        .getValue(
            net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES,
            entity.blockPosition())) {
      fluid = Fluids.FLOWING_LAVA;
      blockFluid = Blocks.LAVA;
    } else {
      fluid = Fluids.FLOWING_WATER;
      blockFluid = Blocks.WATER;
    }
    if (!(entity.level() instanceof ServerLevel world)) return true;
    if (entity instanceof Player player && player.hasEffect(MobEffects.CONDUIT_POWER)) {
      ((StatusEffectInstanceStackHolder) player).queueStatusEffectRemoval(MobEffects.CONDUIT_POWER);
    }

    BlockPos pos = entity.blockPosition();
    var eye = entity.getEyePosition();
    BlockState flowing = blockFluid.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
    int range = (int) Math.floor(REACH + 0.5);
    for (int dx = -range; dx <= range; ++dx)
      for (int dy = -range; dy <= range; ++dy)
        for (int deltaZ = -range; deltaZ <= range; ++deltaZ) {
          double eyeX = pos.getX() + dx + 0.5 - eye.x;
          double eyeY = pos.getY() + dy + 0.5 - eye.y;
          double eyeZ = pos.getZ() + deltaZ + 0.5 - eye.z;
          double distanceSquared = eyeX * eyeX + eyeY * eyeY + eyeZ * eyeZ;
          if (distanceSquared > REACH * REACH) continue;
          BlockPos p = pos.offset(dx, dy, deltaZ);

          BlockState blockState = world.getBlockState(p);
          Block block = blockState.getBlock();

          if (block instanceof FarmlandBlock || block instanceof DirtPathBlock) {
            FarmlandBlock.turnToDirt(null, blockState, world, p);
            blockState = world.getBlockState(p);
            block = blockState.getBlock();
          }

          if (block instanceof LeavesBlock) {
            world.destroyBlock(p, true);
            blockState = world.getBlockState(p);
            block = blockState.getBlock();
          }

          if (!blockState.isCollisionShapeFullBlock(world, p)
              && !(blockState.getFluidState().getType().isSame(fluid))
              && !(block instanceof SoulSandBlock)) {
            float hardness = blockState.getDestroySpeed(world, p);
            if (hardness >= 0 && hardness <= BREAKING_FORCE * (REACH - Math.sqrt(distanceSquared))) {
              world.destroyBlock(p, true);
            }
            blockState = world.getBlockState(p);
            block = blockState.getBlock();
          }

          if (block instanceof AirBlock) {
            world.setBlockAndUpdate(p, flowing);
          }
        }

    return true;
  }
}
