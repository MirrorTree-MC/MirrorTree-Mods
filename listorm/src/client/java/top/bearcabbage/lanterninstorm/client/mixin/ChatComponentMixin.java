package top.bearcabbage.lanterninstorm.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
  @Inject(
      method =
          "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
      at = @At("HEAD"))
  private void storm$repeat(
      Component message,
      MessageSignature signature,
      GuiMessageSource source,
      GuiMessageTag tag,
      CallbackInfo ci) {
    var p = Minecraft.getInstance().player;
    if (p == null
        || p.tickCount < 100
        || !p.isAlive()
        || !p.hasEffect(AnnoyingEffects.REPEATER)
        || source != GuiMessageSource.PLAYER) return;
    String raw = message.getString();
    int split = raw.indexOf("> ");
    if (split < 0 || raw.substring(0, split).endsWith(p.getName().getString())) return;
    String text = raw.substring(split + 2);
    var effect = p.getEffect(AnnoyingEffects.REPEATER);
    if (text.endsWith(" ".repeat(1 + effect.getAmplifier()))) return;
    p.connection.sendChat(text.substring(0, Math.min(255, text.length())) + " ");
  }
}
