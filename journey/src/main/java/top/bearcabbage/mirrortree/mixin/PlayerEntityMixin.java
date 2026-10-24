package top.bearcabbage.mirrortree.mixin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bearcabbage.mirrortree.BedroomInteractions;
import top.bearcabbage.mirrortree.MirrorTree;
@Mixin(Player.class)
public class PlayerEntityMixin {
 @Inject(method="blockActionRestricted",at=@At("HEAD"),cancellable=true)
 private void mirrortree$allowSurvivalShopBreak(Level level, BlockPos pos, GameType gameType,
     CallbackInfoReturnable<Boolean> cir) {
  if (gameType == GameType.ADVENTURE && level.dimension().equals(MirrorTree.bedroom)
      && BedroomInteractions.isSurvivalShop(level.getBlockState(pos).getBlock()))
   cir.setReturnValue(false);
 }
 @Inject(method="tick",at=@At("TAIL"))
 private void mirrortree$restrictDragonRiding(CallbackInfo ci){
  var player=(Player)(Object)this;
  var vehicle=player.getVehicle();
  if(vehicle!=null && player.level().dimension().identifier().getNamespace().equals("starry_skies") && (vehicle instanceof EnderDragon || BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString().equals("adventurez:dragon")))player.stopRiding();
 }
}
