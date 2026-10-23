package top.bearcabbage.lanterninstorm.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
  @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
  private void storm$voiceless(String message, boolean recent, CallbackInfo ci) {
    var p = Minecraft.getInstance().player;
    if (p != null && p.hasEffect(AnnoyingEffects.VOICELESS)) {
      p.sendOverlayMessage(Component.translatable("messages.annoyingeffects.voiceless"));
      ci.cancel();
    }
  }
}
