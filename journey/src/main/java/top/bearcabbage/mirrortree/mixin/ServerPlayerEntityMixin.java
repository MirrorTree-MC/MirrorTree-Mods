package top.bearcabbage.mirrortree.mixin;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bearcabbage.mirrortree.MirrorTree;

@Mixin(ServerPlayer.class)
public class ServerPlayerEntityMixin {
    @Inject(method="drop(Z)V", at=@At("HEAD"), cancellable=true)
    private void mirrortree$protectBedroom(boolean entireStack, CallbackInfo ci) {
        var player = (ServerPlayer)(Object)this;
        if (player.level().dimension().equals(MirrorTree.bedroom) && !player.isCreative()) {
            player.containerMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }
}
