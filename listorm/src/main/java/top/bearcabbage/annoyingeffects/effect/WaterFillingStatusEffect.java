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
import top.bearcabbage.annoyingeffects.effecttags.AdaptableStatusEffectTag;
import top.bearcabbage.annoyingeffects.effecttags.DamageStatusEffectTag;
import top.bearcabbage.annoyingeffects.effecttags.NightMareStatusEffectTag;
import top.bearcabbage.annoyingeffects.effecttags.TerrachangingStatusEffectTag;
import top.bearcabbage.annoyingeffects.utils.NoSavePlayerData;

public class WaterFillingStatusEffect extends AnnoyingStatusEffect
    implements DamageStatusEffectTag,
        AdaptableStatusEffectTag,
        TerrachangingStatusEffectTag,
        NightMareStatusEffectTag {
  public final double REACH = 3F;
  public final double BREAKING_FORCE = 2.5F;

  public WaterFillingStatusEffect() {
    super(); // 显示的颜色
  }

  public static final NoSavePlayerData<Integer> WaterTicks = new NoSavePlayerData<>(0);

  // 这个方法在每个 tick 都会调用，以检查是否应应用药水效果
  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return true;
  }

  // 这个方法在应用药水效果时会被调用，所以我们可以在这里实现自定义功能。
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
    if (entity instanceof Player player) {
      player.removeEffect(MobEffects.CONDUIT_POWER);
    }

    //        boolean createWaterSource = !entity.isSubmergedInWater();

    BlockPos pos = entity.blockPosition();
    int range = (int) Math.floor(REACH + 0.5);
    for (int dx = -range; dx <= range; ++dx)
      for (int dy = -range; dy <= range; ++dy)
        for (int deltaZ = -range; deltaZ <= range; ++deltaZ) {
          BlockPos p = pos.offset(dx, dy, deltaZ);
          double distance = entity.getEyePosition().distanceTo(p.getCenter());
          if (distance > REACH) continue;
          //            placeWater(world, p);

          {
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
              if (hardness >= 0 && hardness <= BREAKING_FORCE * (REACH - distance)) {
                world.destroyBlock(p, true);
              }
              blockState = world.getBlockState(p);
              block = blockState.getBlock();
            }

            if (block instanceof AirBlock
            //                        || block instanceof FluidBlock &&
            // blockState.getFluidState().getType().matchesType(fluid) &&
            // blockState.getFluidState().getLevel() > 1
            ) {
              BlockState flowing = blockFluid.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
              world.setBlockAndUpdate(p, flowing);
              blockState = world.getBlockState(p);
              block = blockState.getBlock();
            }

            //                if(createWaterSource && distance < 1){
            //                    world.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
            //                    blockState = world.getBlockState(p);
            //                    block = blockState.getBlock();
            //                }

            //                if(p.getY() >= 192 && world.getBlockState(p).getBlock() ==
            // Blocks.WATER){
            //                    world.setBlockAndUpdate(p,
            // Blocks.FROSTED_ICE.defaultBlockState());
            //                }

            //                break;
          }
        }

    //        if(!entity.isSubmergedInWater()){
    //            entity.getEyePos().;
    //            entity.setAir(entity.getAir() - 3);
    //            entity.addVelocity(0F, -0.15F, 0F);
    //            entity.setVelocity(entity.getVelocity().add(0F, -0.15F, 0F));
    //        }

    return true;
  }
}
