package top.bearcabbage.lanterninstorm.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bearcabbage.lanterninstorm.progression.ProgressionService;

@Mixin(FoodProperties.class)
public abstract class FoodPropertiesMixin {
  @Inject(method = "onConsume", at = @At("TAIL"))
  private void storm$food(
      Level level, LivingEntity user, ItemStack stack, Consumable consumable, CallbackInfo ci) {
    if (user instanceof ServerPlayer player) ProgressionService.food(player, stack);
  }
}
