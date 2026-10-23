package top.bearcabbage.mirrorskills.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.bearcabbage.mirrorskills.MirrorSkills;
import top.bearcabbage.mirrorskills.data.SkillData;

@Mixin(BowItem.class)
public class BowItemMixin {

    @ModifyVariable(method = "releaseUsing", at = @At("STORE"), ordinal = 0)
    private float modifyPullProgress(float pullProgress, ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (user instanceof Player player) {
            SkillData data = ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).getAttached(MirrorSkills.SKILL_DATA);
            if (data != null && data.isSkillEnabled(SkillData.SKILL_BOW_FAST_CHARGE)) {
                int i = 72000 - remainingUseTicks;
                float f = (float)i / 5.0F; // Standard is / 20.0F. Div 5.0 means 4x faster.
                f = (f * f + f * 2.0F) / 3.0F;
                if (f > 1.0F) {
                    f = 1.0F;
                }
                return f;
            }
        }
        return pullProgress;
    }
}
