package top.bearcabbage.mirrorskills.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bearcabbage.mirrorskills.MirrorSkills;
import top.bearcabbage.mirrorskills.data.SkillData;

@Mixin(Projectile.class)
public abstract class ProjectileEntityMixin {

    @Inject(method = "shootFromRotation", at = @At("TAIL"))
    private void injectSetVelocity(Entity shooter, float pitch, float yaw, float roll, float speed, float divergence, CallbackInfo ci) {
        if (shooter instanceof Player player) {
            SkillData data = ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).getAttached(MirrorSkills.SKILL_DATA);
            if (data != null && data.isSkillEnabled(SkillData.SKILL_BOW_DYNAMIC_CRIT)) {
                
                 if ((Object)this instanceof AbstractArrow arrow) {
                    float f = speed / 3.0F; // Approx charge
                    if (f > 1.0F) f = 1.0F;
                    
                    if (f > 0) {
                         float chance = 0.05F + 0.25F * f;
                         if (arrow.level() != null) {
                             boolean isCrit = arrow.level().getRandom().nextFloat() < chance;
                             if (isCrit) {
                                 arrow.setCritArrow(true);
                                 arrow.setBaseDamage(((ArrowDamageAccessor) arrow).mirrorskills$getBaseDamage() * 2.0);
                                 
                                 if (arrow.level() instanceof net.minecraft.server.level.ServerLevel serverWorld) {
                                     serverWorld.sendParticles(
                                         net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
                                         arrow.getX(), arrow.getY(), arrow.getZ(),
                                         15, 0.2, 0.2, 0.2, 0.1
                                     );
                                     serverWorld.sendParticles(
                                         net.minecraft.core.particles.ParticleTypes.CRIT,
                                         arrow.getX(), arrow.getY(), arrow.getZ(),
                                         10, 0.3, 0.3, 0.3, 0.2
                                     );
                                 }
                             }
                         }
                    }
                }
            }
        }
    }
}
