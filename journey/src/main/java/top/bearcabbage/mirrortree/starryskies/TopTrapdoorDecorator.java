package top.bearcabbage.mirrortree.starryskies;

import com.mojang.serialization.Codec;
import de.dafuqs.starryskies.registries.StarryRegistries;
import de.dafuqs.starryskies.worldgen.PlacedSphere;
import de.dafuqs.starryskies.worldgen.SphereDecorator;
import de.dafuqs.starryskies.worldgen.SphereDecoratorConfig;
import de.dafuqs.starryskies.worldgen.SphereFeatureContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import static net.minecraft.world.level.block.Blocks.EXPOSED_COPPER_GRATE;
import static net.minecraft.world.level.block.Blocks.OXIDIZED_COPPER_BULB;
import static net.minecraft.world.level.block.Blocks.TINTED_GLASS;
import static top.bearcabbage.mirrortree.MirrorTree.MOD_ID;

public class TopTrapdoorDecorator extends SphereDecorator<SphereDecoratorConfig.DefaultSphereDecoratorConfig> {
    public static final SphereDecorator<SphereDecoratorConfig.DefaultSphereDecoratorConfig> TOPTRAPDOOR =
            Registry.register(StarryRegistries.SPHERE_DECORATOR,
                    Identifier.fromNamespaceAndPath(MOD_ID, "toptrapdoor"),
                    new TopTrapdoorDecorator(SphereDecoratorConfig.DefaultSphereDecoratorConfig.CODEC));

    private static final BlockState GLASS = TINTED_GLASS.defaultBlockState();
    private static final BlockState LIT_BULB = OXIDIZED_COPPER_BULB.defaultBlockState().cycle(BlockStateProperties.LIT);
    private static final BlockState GRATE = EXPOSED_COPPER_GRATE.defaultBlockState();

    // Called by the mod initializer to load this class and register the decorator.
    public static void init() {}

    public TopTrapdoorDecorator(Codec<SphereDecoratorConfig.DefaultSphereDecoratorConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(SphereFeatureContext<SphereDecoratorConfig.DefaultSphereDecoratorConfig> context) {
        WorldGenLevel world = context.world();
        PlacedSphere<?> sphere = context.sphere();
        BlockPos center = sphere.getPosition();
        int radius = sphere.getRadius() + 2;

        // 顶部中央表面玻璃块
        BlockPos topCenter = center.above(radius);
        while (world.getBlockState(topCenter).isAir() && topCenter.getY() > center.getY()) {
            topCenter = topCenter.below();
        }
        world.setBlock(topCenter, GLASS, 3);

        // 三正交方向圆环；保留遍历和随机调用顺序，包括圆环交叠处的多次放置。
        float innerRadius = radius + 2;
        int ringWidth = Math.max(1, Math.min(2, Math.round(radius * 0.1F)));
        float outerRadius = innerRadius + ringWidth;
        double innerSquared = (double) innerRadius * innerRadius;
        double outerSquared = (double) outerRadius * outerRadius;
        double verticalThickness = radius / 4.0;
        int extent = (int) Math.ceil(outerRadius);
        int centerX = center.getX(), centerY = center.getY(), centerZ = center.getZ();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        RandomSource random = context.random();

        for (int x = centerX - extent; x <= centerX + extent; x++) {
            double dx = x - centerX, dxSquared = dx * dx;
            for (int y = centerY - extent; y <= centerY + extent; y++) {
                double dy = y - centerY, dySquared = dy * dy;
                double distXY = dxSquared + dySquared;
                for (int z = centerZ - extent; z <= centerZ + extent; z++) {
                    mutablePos.set(x, y, z);
                    double dz = z - centerZ, dzSquared = dz * dz;
                    if (Math.abs(dz) < verticalThickness && distXY >= innerSquared && distXY <= outerSquared) {
                        world.setBlock(mutablePos, random.nextFloat() > 0.6F ? LIT_BULB : GRATE, 3);
                    }
                    double distXZ = dxSquared + dzSquared;
                    if (Math.abs(dy) < verticalThickness && distXZ >= innerSquared && distXZ <= outerSquared) {
                        world.setBlock(mutablePos, random.nextFloat() > 0.6F ? LIT_BULB : GRATE, 3);
                    }
                    double distYZ = dySquared + dzSquared;
                    if (Math.abs(dx) < verticalThickness && distYZ >= innerSquared && distYZ <= outerSquared) {
                        world.setBlock(mutablePos, random.nextFloat() > 0.6F ? LIT_BULB : GRATE, 3);
                    }
                }
            }
        }
        return true;
    }
}
