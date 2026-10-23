package top.bearcabbage.mirrorskills.mixin;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bearcabbage.mirrorskills.MirrorSkills;
import top.bearcabbage.mirrorskills.data.SkillData;

@Mixin(Player.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void mirrorskills$invincibleDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        SkillData data = ((AttachmentTarget) this).getAttached(MirrorSkills.SKILL_DATA);
        if (data != null && data.getParameter("invincible_ticks") > 0) {
            cir.setReturnValue(false);
        }
    }

    @ModifyConstant(method = "attack", constant = @Constant(floatValue = 1.5F))
    private float mirrorskills$modifyCritDamage(float original) {
        SkillData data = ((AttachmentTarget) this).getAttached(MirrorSkills.SKILL_DATA);
        if (data != null && data.isSkillEnabled(SkillData.SKILL_CRIT_MULTIPLIER)) {
            float multiplier = data.getParameter("crit_multiplier");
            return multiplier > 0 ? multiplier : 1.5F;
        }
        return original;
    }
}
