package top.bearcabbage.lanterninstorm.mixin;

import net.minecraft.advancements.*;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bearcabbage.lanterninstorm.progression.ProgressionService;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
  @Shadow private ServerPlayer player;

  @Shadow
  public abstract AdvancementProgress getOrStartProgress(AdvancementHolder advancement);

  @Inject(method = "award", at = @At("RETURN"))
  private void storm$advancement(
      AdvancementHolder entry, String criterion, CallbackInfoReturnable<Boolean> cir) {
    if (!cir.getReturnValue() || !getOrStartProgress(entry).isDone()) return;
    var id = entry.id();
    if (id.getNamespace().equals("minecraft"))
      entry
          .value()
          .display()
          .ifPresent(
              display -> {
                int amount =
                    switch (display.getType()) {
                      case GOAL -> 1;
                      case CHALLENGE -> 5;
                      default -> 0;
                    };
                if (amount > 0)
                  ProgressionService.award(player, "advancement", id.toString(), amount);
              });
    else if (id.getNamespace().equals("spirit-in-storm") && id.getPath().contains("egg/"))
      ProgressionService.award(player, "easter-egg", id.toString(), 1);
  }
}
