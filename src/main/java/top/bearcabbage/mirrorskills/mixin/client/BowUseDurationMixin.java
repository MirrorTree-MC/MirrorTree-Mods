package top.bearcabbage.mirrorskills.mixin.client;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bearcabbage.mirrorskills.MirrorSkills;
import top.bearcabbage.mirrorskills.data.SkillData;
@Mixin(UseDuration.class)
public class BowUseDurationMixin {
 @Inject(method="useDuration",at=@At("RETURN"),cancellable=true)
 private static void mirrorskills$fastBow(ItemStack stack,LivingEntity entity,CallbackInfoReturnable<Integer> cir){
  var data=((AttachmentTarget)entity).getAttached(MirrorSkills.SKILL_DATA);
  if(stack.is(Items.BOW)&&data!=null&&data.isSkillEnabled(SkillData.SKILL_BOW_FAST_CHARGE))cir.setReturnValue(cir.getReturnValue()*4);
 }
}
