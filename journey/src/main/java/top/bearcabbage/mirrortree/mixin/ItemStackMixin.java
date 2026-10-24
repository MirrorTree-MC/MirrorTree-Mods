package top.bearcabbage.mirrortree.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bearcabbage.mirrortree.BedroomInteractions;
import top.bearcabbage.mirrortree.MirrorTree;

@Mixin(ItemStack.class)
public class ItemStackMixin {
 @Inject(method="canPlaceOnBlockInAdventureMode",at=@At("RETURN"),cancellable=true)
 private void mirrortree$allowSurvivalShopPlacement(BlockInWorld clicked,
     CallbackInfoReturnable<Boolean> cir) {
  if (!cir.getReturnValue() && clicked.getLevel() instanceof Level level
      && level.dimension().equals(MirrorTree.bedroom)
      && BedroomInteractions.isSurvivalShopItem((ItemStack)(Object)this))
   cir.setReturnValue(true);
 }
}
