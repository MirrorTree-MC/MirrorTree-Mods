package top.bearcabbage.mirrortree.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bearcabbage.mirrortree.MirrorTree;
import top.bearcabbage.mirrortree.dream.MTDream;

@Mixin(ServerPlayer.class)
public class ServerPlayerEntityMixin {
    @WrapMethod(method="teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;")
    private ServerPlayer mirrortree$settleDream(TeleportTransition transition, Operation<ServerPlayer> original) {
        return MTDream.teleport((ServerPlayer)(Object)this, transition.newLevel(), () -> original.call(transition));
    }

    @Inject(method="drop(Z)V", at=@At("HEAD"), cancellable=true)
    private void mirrortree$protectBedroom(boolean entireStack, CallbackInfo ci) {
        var player = (ServerPlayer)(Object)this;
        if (player.level().dimension().equals(MirrorTree.bedroom) && !player.isCreative()) {
            player.containerMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }
}
