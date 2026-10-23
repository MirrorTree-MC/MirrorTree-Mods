package top.bearcabbage.mirrortree.mixin;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(FoodData.class)
public interface FoodDataAccessor {
 @Accessor("exhaustionLevel") float mirrortree$getExhaustion();
 @Accessor("exhaustionLevel") void mirrortree$setExhaustion(float value);
}
