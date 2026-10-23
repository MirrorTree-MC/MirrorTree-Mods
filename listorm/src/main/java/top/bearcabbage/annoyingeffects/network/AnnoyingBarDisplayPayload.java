package top.bearcabbage.annoyingeffects.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AnnoyingBarDisplayPayload(boolean display) implements CustomPacketPayload {
  public static final Type<AnnoyingBarDisplayPayload> ID =
      new Type<>(Identifier.fromNamespaceAndPath("annoyingeffects", "annoyingbar-display-payload"));
  public static final StreamCodec<FriendlyByteBuf, AnnoyingBarDisplayPayload> CODEC =
      StreamCodec.of(
          (buf, value) -> buf.writeBoolean(value.display()),
          buf -> new AnnoyingBarDisplayPayload(buf.readBoolean()));

  public Type<? extends CustomPacketPayload> type() {
    return ID;
  }
}
