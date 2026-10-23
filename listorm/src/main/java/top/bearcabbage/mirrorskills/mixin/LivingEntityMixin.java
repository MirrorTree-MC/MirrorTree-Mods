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
        if ((Object) this instanceof ServerPlayer player) {
            SkillData data = ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA);
            
            // Skill 5: Potion Time Multiplier
            if (data.isSkillEnabled(SkillData.SKILL_POTION_TIME)) {
                StatusEffectInstanceExtensions ext = (StatusEffectInstanceExtensions) effect;
                if (!ext.mirrorskills$isDurationModified() && !effect.isInfiniteDuration()) {
                    float harmfulReduction = data.getParameter("harmful_reduction");
                    float beneficialIncrease = data.getParameter("beneficial_increase");
                    
                    int duration = effect.getDuration();
                    if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                         // formula: base * (1 - x)
                         duration = (int) (duration * Math.max(0, 1.0f - harmfulReduction));
                    } else if (effect.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) {
                         // formula: base * (1 + y)
                         duration = (int) (duration * (1.0f + beneficialIncrease));
                    }
                    
                    if (duration != effect.getDuration()) {
                         // Helper: create copy with new duration
                         MobEffectInstance newEffect = new MobEffectInstance(
                             effect.getEffect(),
                             duration,
                             effect.getAmplifier(),
                             effect.isAmbient(),
                             effect.isVisible(),
                             effect.showIcon(),
                             ext.mirrorskills$getHiddenEffect()
                         );
                         
                         StatusEffectInstanceExtensions newExt = (StatusEffectInstanceExtensions) newEffect;
                         newExt.mirrorskills$setDurationModified(true);
                         newExt.mirrorskills$setLevelModified(ext.mirrorskills$isLevelModified());
                         
                         effect = newEffect;
                    }
                }
            }

            // Skill 6: Potion Level Adjustment
            if (data.isSkillEnabled(SkillData.SKILL_POTION_LEVEL)) {
                StatusEffectInstanceExtensions ext = (StatusEffectInstanceExtensions) effect;
                if (!ext.mirrorskills$isLevelModified()) {
                    float adjustment = data.getParameter("level_adjustment");
                    int ampChange = (int) adjustment;
                    
                    if (ampChange != 0) {
                        int currentAmp = effect.getAmplifier();
                        int newAmp = currentAmp;
    
                        if (effect.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) {
                            newAmp += ampChange;
                        } else if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                            // "level-down all harmful effect (min lv.1)" -> Amplifier 0 is Lv.1
                            newAmp -= ampChange;
                            if (newAmp < 0) newAmp = 0;
                        }
                        
                        if (newAmp != currentAmp) {
                             MobEffectInstance newEffect = new MobEffectInstance(
                                 effect.getEffect(),
                                 effect.getDuration(),
                                 newAmp,
                                 effect.isAmbient(),
                                 effect.isVisible(),
                                 effect.showIcon(),
                                 ext.mirrorskills$getHiddenEffect()
                             );
                             
                             StatusEffectInstanceExtensions newExt = (StatusEffectInstanceExtensions) newEffect;
                             newExt.mirrorskills$setLevelModified(true);
                             newExt.mirrorskills$setDurationModified(ext.mirrorskills$isDurationModified());
                             
                             effect = newEffect;
                        }
                    }
                }
            }
        }
        return effect;
    }
}
