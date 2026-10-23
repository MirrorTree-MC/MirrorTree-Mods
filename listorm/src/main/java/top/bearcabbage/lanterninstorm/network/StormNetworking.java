package top.bearcabbage.lanterninstorm.network;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.level.ServerPlayer;
import top.bearcabbage.annoyingeffects.network.*;

public final class StormNetworking {
  private record Bar(boolean display, int stage) {}

  private static final Map<UUID, Bar> LAST = new HashMap<>();

  public static void initialize() {
    PayloadTypeRegistry.clientboundPlay()
        .register(AnnoyingBarDisplayPayload.ID, AnnoyingBarDisplayPayload.CODEC);
    PayloadTypeRegistry.clientboundPlay()
        .register(AnnoyingBarStagePayload.ID, AnnoyingBarStagePayload.CODEC);
  }

  public static void bar(ServerPlayer player, boolean display, int stage) {
    Bar prev = LAST.get(player.getUUID());
    if (stage < 0) stage = prev == null ? 0 : prev.stage;
    stage = Math.clamp(stage, 0, 6);
    if (prev == null || prev.display != display)
      ServerPlayNetworking.send(player, new AnnoyingBarDisplayPayload(display));
    if (prev == null || prev.stage != stage)
      ServerPlayNetworking.send(player, new AnnoyingBarStagePayload(stage));
    LAST.put(player.getUUID(), new Bar(display, stage));
  }

  public static void forget(UUID player) {
    LAST.remove(player);
  }

  public static void clear() {
    LAST.clear();
  }
}
