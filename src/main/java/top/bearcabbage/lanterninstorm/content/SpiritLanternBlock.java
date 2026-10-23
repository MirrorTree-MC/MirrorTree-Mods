package top.bearcabbage.lanterninstorm.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import top.bearcabbage.lanterninstorm.safety.LanternIndex;

public final class SpiritLanternBlock extends LanternBlock {
  public static final BooleanProperty STARTUP = BooleanProperty.create("startup");
  public static final EnumProperty<net.minecraft.core.Direction> FACING =
      BlockStateProperties.HORIZONTAL_FACING;
  public static final MapCodec<SpiritLanternBlock> CODEC =
      com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(
          i ->
              i.group(
                      propertiesCodec(),
                      com.mojang.serialization.Codec.intRange(0, 16)
                          .fieldOf("radius")
                          .forGetter(SpiritLanternBlock::radius))
                  .apply(i, SpiritLanternBlock::new));
  private final int radius;

  public SpiritLanternBlock(BlockBehaviour.Properties properties, int radius) {
    super(properties);
    this.radius = radius;
    registerDefaultState(
        defaultBlockState()
            .setValue(STARTUP, false)
            .setValue(FACING, net.minecraft.core.Direction.NORTH));
  }

  public int radius() {
    return radius;
  }

  @Override
  public MapCodec<SpiritLanternBlock> codec() {
    return CODEC;
  }

  @Override
  protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
    return true;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    super.createBlockStateDefinition(builder);
    builder.add(STARTUP, FACING);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    BlockState state = super.getStateForPlacement(context);
    return state == null ? null : state.setValue(FACING, context.getHorizontalDirection());
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return state.getValue(HANGING)
        ? Block.box(3, 2, 3, 13, 12, 13)
        : Block.box(3, 3, 3, 13, 13, 13);
  }

  @Override
  public void animateTick(
      BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
    if (state.getValue(STARTUP))
      BorderParticles.box(
          level,
          net.minecraft.world.phys.Vec3.atCenterOf(pos),
          level.dimension().identifier().getNamespace().equals("minecraft") ? 8 : radius,
          false);
  }

  @Override
  protected void onPlace(
      BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
    super.onPlace(state, level, pos, oldState, moved);
    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel)
      LanternIndex.add(serverLevel, pos);
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (level.isClientSide()) return InteractionResult.SUCCESS;
    if (!level.dimension().identifier().getNamespace().equals("minecraft") && radius == 0) {
      player.sendOverlayMessage(Component.literal("这种彩灯抵御不住球球世界的噩梦侵蚀"));
      return InteractionResult.SUCCESS;
    }
    BlockState changed = state.cycle(STARTUP);
    level.setBlockAndUpdate(pos, changed);
    if (changed.getValue(STARTUP)
        && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
      int effectiveRadius =
          level.dimension().identifier().getNamespace().equals("minecraft") ? 8 : radius;
      serverPlayer.connection.send(
          new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
              Component.literal("又点亮了一盏彩灯！").withColor(0xfca106)));
      serverPlayer.connection.send(
          new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
              Component.literal("附近半径" + effectiveRadius + "的立方体稳定下来了").withColor(0xbbbbbb)));
    } else player.sendOverlayMessage(Component.literal("这盏彩灯被关掉了"));
    return InteractionResult.SUCCESS;
  }
}
