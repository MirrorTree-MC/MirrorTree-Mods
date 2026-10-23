package top.bearcabbage.mirrorskills.mixin;

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

    // Invincible State Logic: Block damage if active
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void mirrorskills$invincibleDamage(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        // Check attachment
        SkillData data = ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).getAttached(MirrorSkills.SKILL_DATA);
        if (data != null && data.getParameter("invincible_ticks") > 0) {
            // Block damage
            cir.setReturnValue(false);
        }
    }

    // Critical Hit Multiplier Logic
    @ModifyConstant(method = "attack", constant = @Constant(floatValue = 1.5F))
    private float mirrorskills$modifyCritDamage(float original) {
        Player player = (Player) (Object) this;
        SkillData data = ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).getAttached(MirrorSkills.SKILL_DATA);
        if (data != null) {
            // Check if crit skill is "enabled" or just use the param if explicit?
            // User requested "control ... under mirrorskills command".
            // Assuming "grant" enables it, "param" sets multiplier.
            // If SKILL_CRIT_MULTIPLIER is enabled, use param.
            if (data.isSkillEnabled(SkillData.SKILL_CRIT_MULTIPLIER)) {
                 float mult = data.getParameter("crit_multiplier");
                 // If mult is 0 (default if not set), use 1.5 or fallback? 
                 // If default from param map is 0.0f, then we potentially nerf crits.
                 // Better logic: if mult > 0, use it. Else use original.
                 if (mult > 0) {
                     return mult;
                 }
                 // If user set param to 1.5, it returns 1.5. If user set 0.0, well, they asked for 0.
                 // Wait, data.getParameter returns 0.0 if not found.
                 // Default multiplier is 1.5. 
                 // Strategy: If skill enabled and param not set, maybe default 1.5? 
                 // Or require setting param. 
                 // Let's assume default to 1.5 if 0.
                 return mult > 0 ? mult : 1.5f;
            }
        }
        return original;
    }
}
