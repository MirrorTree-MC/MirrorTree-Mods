package top.bearcabbage.mirrorskills.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.bearcabbage.mirrorskills.MirrorSkills;
import top.bearcabbage.mirrorskills.data.SkillData;
import top.bearcabbage.mirrorskills.util.StatusEffectInstanceExtensions;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true)
    private MobEffectInstance modifyEffect(MobEffectInstance effect) {
        if (!((Object) this instanceof ServerPlayer player)) return effect;
        SkillData data = ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA);
        var flags = (StatusEffectInstanceExtensions) effect;
        var category = effect.getEffect().value().getCategory();
        int duration = effect.getDuration(), amplifier = effect.getAmplifier();

        if (data.isSkillEnabled(SkillData.SKILL_POTION_TIME)
                && !flags.mirrorskills$isDurationModified() && !effect.isInfiniteDuration()) {
            if (category == MobEffectCategory.HARMFUL)
                duration = (int) (duration * Math.max(0, 1.0f - data.getParameter("harmful_reduction")));
            else if (category == MobEffectCategory.BENEFICIAL)
                duration = (int) (duration * (1.0f + data.getParameter("beneficial_increase")));
        }
        if (data.isSkillEnabled(SkillData.SKILL_POTION_LEVEL) && !flags.mirrorskills$isLevelModified()) {
            int adjustment = (int) data.getParameter("level_adjustment");
            if (adjustment != 0) {
                if (category == MobEffectCategory.BENEFICIAL) amplifier += adjustment;
                else if (category == MobEffectCategory.HARMFUL) amplifier = Math.max(0, amplifier - adjustment);
            }
        }
        boolean durationChanged = duration != effect.getDuration();
        boolean levelChanged = amplifier != effect.getAmplifier();
        if (!durationChanged && !levelChanged) return effect;

        // Rebuild once while retaining the hidden chain and each field's provenance.
        var adjusted = new MobEffectInstance(effect.getEffect(), duration, amplifier,
                effect.isAmbient(), effect.isVisible(), effect.showIcon(), flags.mirrorskills$getHiddenEffect());
        var adjustedFlags = (StatusEffectInstanceExtensions) adjusted;
        adjustedFlags.mirrorskills$setDurationModified(durationChanged || flags.mirrorskills$isDurationModified());
        adjustedFlags.mirrorskills$setLevelModified(levelChanged || flags.mirrorskills$isLevelModified());
        return adjusted;
    }
}
