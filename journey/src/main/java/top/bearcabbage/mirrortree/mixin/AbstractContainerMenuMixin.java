package top.bearcabbage.mirrortree.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bearcabbage.mirrortree.MirrorTree;

@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {
    @Inject(method="clicked", at=@At("HEAD"), cancellable=true)
    private void mirrortree$protectBedroom(int slot, int button, ContainerInput input,
            Player player, CallbackInfo ci) {
        // Both sides must reject the action before the cursor or slot loses its stack.
        if (player.level().dimension().equals(MirrorTree.bedroom) && !player.isCreative()
                && (input == ContainerInput.THROW || slot == -999 && input == ContainerInput.PICKUP))
            ci.cancel();
    }
}
