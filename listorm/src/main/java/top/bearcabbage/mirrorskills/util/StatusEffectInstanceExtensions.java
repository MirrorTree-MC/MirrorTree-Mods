package top.bearcabbage.mirrorskills.util;

import net.minecraft.world.effect.MobEffectInstance;

public interface StatusEffectInstanceExtensions {
    MobEffectInstance mirrorskills$getHiddenEffect();

    boolean mirrorskills$isDurationModified();
    void mirrorskills$setDurationModified(boolean modified);

    boolean mirrorskills$isLevelModified();
    void mirrorskills$setLevelModified(boolean modified);
}
