package top.bearcabbage.mirrortree.mixin;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bearcabbage.mirrortree.MirrorTree;
@Mixin(LocalPlayer.class)
public class ClientPlayerEntityMixin {
 @Inject(method="drop",at=@At("HEAD"),cancellable=true)
 private void mirrortree$protectBedroom(boolean entireStack,CallbackInfoReturnable<Boolean> cir){
  var player=(LocalPlayer)(Object)this;
  if(player.level().dimension().equals(MirrorTree.bedroom)&&!player.isCreative()){
   Minecraft.getInstance().gui.setOverlayMessage(Component.literal("在狐狸家里不能丢东西哦"),false);
   cir.setReturnValue(false);
  }
 }
}
