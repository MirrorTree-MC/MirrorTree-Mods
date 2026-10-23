package top.bearcabbage.mirrortree.starryskies;

import com.mojang.serialization.Codec;
import de.dafuqs.starryskies.StarrySkies;
import de.dafuqs.starryskies.registries.StarryRegistries;
import de.dafuqs.starryskies.worldgen.*;
import de.dafuqs.starryskies.worldgen.decorators.XMarksTheSpotDecorator;
import de.dafuqs.starryskies.worldgen.decorators.XMarksTheSpotDecoratorConfig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;

import static net.minecraft.world.level.block.Blocks.*;
import static top.bearcabbage.mirrortree.MirrorTree.MOD_ID;

public class TopTrapdoorDecorator extends SphereDecorator<SphereDecoratorConfig.DefaultSphereDecoratorConfig> {

    public static SphereDecorator<SphereDecoratorConfig.DefaultSphereDecoratorConfig> TOPTRAPDOOR = register("toptrapdoor", new TopTrapdoorDecorator(SphereDecoratorConfig.DefaultSphereDecoratorConfig.CODEC));


    private static final BlockState GLASS = TINTED_GLASS.defaultBlockState();

    public static void init() {}

    private static <C extends SphereDecoratorConfig, F extends SphereDecorator<C>> F register(String name, F feature) {
        return Registry.register(StarryRegistries.SPHERE_DECORATOR, Identifier.fromNamespaceAndPath(MOD_ID, name), feature);
    }

    public TopTrapdoorDecorator(Codec<SphereDecoratorConfig.DefaultSphereDecoratorConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(SphereFeatureContext<SphereDecoratorConfig.DefaultSphereDecoratorConfig> context) {
        WorldGenLevel world = context.world();
        PlacedSphere<?> sphere = context.sphere();

        BlockPos center = sphere.getPosition();
        int radius = sphere.getRadius() + 2;

        // -------------------------------
        // 顶部中央表面玻璃块
        // -------------------------------
        BlockPos topCenter = center.above(radius);
        while (world.getBlockState(topCenter).isAir() && topCenter.getY() > center.getY()) {
            topCenter = topCenter.below();
        }
        world.setBlock(topCenter, GLASS, 3);

        // -------------------------------
        // 三正交方向圆环逻辑（Copper Bulb/Copper Grate）
        // -------------------------------
        float innerRadius = radius + 2; // 内径
        int ringWidth = Math.max(1, Math.min(2, Math.round(radius * 0.1F))); // 自适应宽度 1-2 方块
        float outerRadius = innerRadius + ringWidth;
        double verticalThickness = radius/4.0; // 半径>4时垂直方向加宽

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        RandomSource random = context.random();

        int minX = center.getX() - (int)Math.ceil(outerRadius);
        int maxX = center.getX() + (int)Math.ceil(outerRadius);
        int minY = center.getY() - (int)Math.ceil(outerRadius);
        int maxY = center.getY() + (int)Math.ceil(outerRadius);
        int minZ = center.getZ() - (int)Math.ceil(outerRadius);
        int maxZ = center.getZ() + (int)Math.ceil(outerRadius);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);

                    double dx = x - center.getX();
                    double dy = y - center.getY();
                    double dz = z - center.getZ();

                    // XY平面圆环
                    double distXY = Math.sqrt(dx*dx + dy*dy);
                    if (distXY >= innerRadius && distXY <= outerRadius && Math.abs(dz) < verticalThickness) {
                        world.setBlock(mutablePos, random.nextFloat() > 0.6F ?
                                OXIDIZED_COPPER_BULB.defaultBlockState().cycle(BlockStateProperties.LIT) :
                                EXPOSED_COPPER_GRATE.defaultBlockState(), 3);
                    }

                    // XZ平面圆环
                    double distXZ = Math.sqrt(dx*dx + dz*dz);
                    if (distXZ >= innerRadius && distXZ <= outerRadius && Math.abs(dy) < verticalThickness) {
                        world.setBlock(mutablePos, random.nextFloat() > 0.6F ?
                                OXIDIZED_COPPER_BULB.defaultBlockState().cycle(BlockStateProperties.LIT) :
                                EXPOSED_COPPER_GRATE.defaultBlockState(), 3);
                    }

                    // YZ平面圆环
                    double distYZ = Math.sqrt(dy*dy + dz*dz);
                    if (distYZ >= innerRadius && distYZ <= outerRadius && Math.abs(dx) < verticalThickness) {
                        world.setBlock(mutablePos, random.nextFloat() > 0.6F ?
                                OXIDIZED_COPPER_BULB.defaultBlockState().cycle(BlockStateProperties.LIT) :
                                EXPOSED_COPPER_GRATE.defaultBlockState(), 3);
                    }
                }
            }
        }

        return true;
    }
}
