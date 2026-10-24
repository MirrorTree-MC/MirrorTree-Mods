package top.bearcabbage.mirrortree.mixin;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bearcabbage.mirrortree.MirrorTree;

@Mixin(Minecraft.class)
public class MinecraftMixin {
 @Inject(method="createTitle",at=@At("HEAD"),cancellable=true)
 private void mirrortree$windowTitle(CallbackInfoReturnable<String> cir){
  cir.setReturnValue("MirrorTree · "+MirrorTree.SERVER_SEASON_VERSION
   +" | "+SharedConstants.getCurrentVersion().name()+"-Fabric | Ciallo～(∠・ω< )⌒★");
 }
}
