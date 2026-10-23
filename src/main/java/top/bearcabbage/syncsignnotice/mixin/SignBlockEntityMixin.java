package top.bearcabbage.syncsignnotice.mixin;

import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bearcabbage.syncsignnotice.SyncSignNotice;

@Mixin(SignBlockEntity.class)
public abstract class SignBlockEntityMixin {
    @Shadow private SignText frontText;

    @Inject(method = "getFrontText", at = @At("HEAD"), cancellable = true)
    private void noticeText(CallbackInfoReturnable<SignText> cir) {
        cir.setReturnValue(SyncSignNotice.noticeFor(frontText));
    }
}
