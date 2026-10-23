package top.bearcabbage.mirrortree.mixin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Player.class)
public class PlayerEntityMixin {
 @Inject(method="tick",at=@At("TAIL"))
 private void mirrortree$restrictDragonRiding(CallbackInfo ci){
  var player=(Player)(Object)this;
  var vehicle=player.getVehicle();
  if(vehicle!=null && player.level().dimension().identifier().getNamespace().equals("starry_skies") && (vehicle instanceof EnderDragon || BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString().equals("adventurez:dragon")))player.stopRiding();
 }
}
