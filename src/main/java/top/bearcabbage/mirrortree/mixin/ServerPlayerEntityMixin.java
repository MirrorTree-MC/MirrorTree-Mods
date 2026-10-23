package top.bearcabbage.mirrortree.mixin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bearcabbage.mirrortree.MirrorTree;

@Mixin(ServerPlayer.class)
public class ServerPlayerEntityMixin {
    @Inject(method="drop", at=@At("HEAD"), cancellable=true)
    private void mirrortree$protectBedroom(ItemStack stack, boolean random, boolean retain,
            CallbackInfoReturnable<ItemEntity> cir) {
        var player = (ServerPlayer)(Object)this;
        if (player.level().dimension().equals(MirrorTree.bedroom) && !player.isCreative())
            cir.setReturnValue(null);
    }
}
