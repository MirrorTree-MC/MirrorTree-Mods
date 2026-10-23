package top.bearcabbage.annoyingeffects.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AnnoyingBarStagePayload(int stage) implements CustomPacketPayload {
  public static final Type<AnnoyingBarStagePayload> ID =
      new Type<>(Identifier.fromNamespaceAndPath("annoyingeffects", "annoyingbar-stage-payload"));
  public static final StreamCodec<FriendlyByteBuf, AnnoyingBarStagePayload> CODEC =
      StreamCodec.of(
          (buf, value) -> buf.writeInt(value.stage()),
          buf -> new AnnoyingBarStagePayload(buf.readInt()));

  public Type<? extends CustomPacketPayload> type() {
    return ID;
  }
}
