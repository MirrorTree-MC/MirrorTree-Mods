package top.bearcabbage.mirrorskills.mixin.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bearcabbage.mirrorskills.MirrorSkills;
import top.bearcabbage.mirrorskills.data.SkillData;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void injectGetFovMultiplier(CallbackInfoReturnable<Float> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        if (player.isUsingItem()) {
            ItemStack stack = player.getUseItem();
            
            // Check for Bow and Fast Charge Skill
            if (stack.is(Items.BOW)) {
                SkillData data = ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).getAttached(MirrorSkills.SKILL_DATA);
                if (data != null && data.isSkillEnabled(SkillData.SKILL_BOW_FAST_CHARGE)) {
                    int useTimeTicks = stack.getUseDuration(player) - player.getUseItemRemainingTicks();
                    float useTime = (float) useTimeTicks;

                    // Vanilla logic uses 20.0F
                    float gVanilla = useTime / 20.0F;
                    if (gVanilla > 1.0F) {
                        gVanilla = 1.0F;
                    } else {
                        gVanilla *= gVanilla;
                    }
                    float vanillaMultiplier = 1.0F - gVanilla * 0.15F;

                    // Our fast charge logic uses 5.0F (4x speed)
                    float gCustom = useTime / 5.0F;
                    if (gCustom > 1.0F) {
                        gCustom = 1.0F;
                    } else {
                        gCustom *= gCustom;
                    }
                    float customMultiplier = 1.0F - gCustom * 0.15F;

                    // Adjust the returned FOV multiplier
                    // Remove vanilla effect, apply custom effect
                    float currentFov = cir.getReturnValue();
                    if (vanillaMultiplier != 0) {
                        float baseFov = currentFov / vanillaMultiplier;
                         cir.setReturnValue(baseFov * customMultiplier);
                    }
                }
            }
        }
    }
}
