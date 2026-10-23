package top.bearcabbage.mirrorskills.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bearcabbage.mirrorskills.network.ModNetworking;
import top.bearcabbage.mirrorskills.data.SkillData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @Shadow public HitResult hitResult;
    @Shadow public net.minecraft.client.player.LocalPlayer player;

    @Inject(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void onDoAttack(CallbackInfoReturnable<Boolean> cir) {
        if (this.hitResult != null && this.hitResult.getType() == HitResult.Type.MISS) {
            if (this.player != null) {
                boolean holdingWeapon = this.player.getMainHandItem().is(Items.BOW) 
                                     || this.player.getMainHandItem().is(Items.CROSSBOW)
                                     || this.player.getOffhandItem().is(Items.BOW)
                                     || this.player.getOffhandItem().is(Items.CROSSBOW);
                                     
                if (holdingWeapon && ClientPlayNetworking.canSend(ModNetworking.TRIGGER_SKILL_ID)) {
                    ClientPlayNetworking.send(new ModNetworking.TriggerSkillPayload(SkillData.SKILL_TIPPED_ARROW));
                }
            }
        }
    }
}
