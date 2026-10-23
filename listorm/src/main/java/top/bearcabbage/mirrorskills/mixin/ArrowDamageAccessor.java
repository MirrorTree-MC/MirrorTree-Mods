package top.bearcabbage.mirrorskills.mixin;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(AbstractArrow.class)
public interface ArrowDamageAccessor {
 @Accessor("baseDamage") double mirrorskills$getBaseDamage();
}
