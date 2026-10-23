package top.bearcabbage.lanterninstorm;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import org.slf4j.*;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;
import top.bearcabbage.lanterninstorm.content.*;
import top.bearcabbage.lanterninstorm.effects.*;
import top.bearcabbage.lanterninstorm.network.*;
import top.bearcabbage.lanterninstorm.progression.*;
import top.bearcabbage.lanterninstorm.safety.*;

public final class LanternInStorm implements ModInitializer {
  public static final String MOD_ID = "lantern-in-storm";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  @Override
  public void onInitialize() {
    new AnnoyingEffects().onInitialize();
    StormContent.initialize();
    BeginningLanternEntity.initialize();
    BorderParticles.initialize();
    StormNetworking.initialize();
    EffectRuntime.initialize();
    SpiritExperienceSource.register();
    ServerChunkEvents.CHUNK_LOAD.register(
        (level, chunk, newChunk) -> LanternIndex.load(level, chunk));
    ServerChunkEvents.CHUNK_UNLOAD.register(LanternIndex::unload);
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            EffectRuntime.tick(player);
            SafetyService.tick(player);
            ProgressionService.tick(player);
          }
        });
    ServerPlayerEvents.JOIN.register(PlayerStore::get);
    ServerPlayerEvents.LEAVE.register(
        player -> {
          PlayerStore.leave(player);
          EffectRuntime.leave(player);
          StormNetworking.forget(player.getUUID());
        });
    ServerPlayerEvents.AFTER_RESPAWN.register(
        (oldPlayer, newPlayer, alive) -> {
          PlayerStore.get(newPlayer).graceTicks = 200;
          EffectRuntime.leave(newPlayer);
          StormNetworking.forget(newPlayer.getUUID());
        });
    ServerLifecycleEvents.SERVER_STOPPING.register(
        server -> server.getPlayerList().getPlayers().forEach(PlayerStore::save));
    ServerLifecycleEvents.SERVER_STOPPED.register(
        server -> {
          PlayerStore.clear();
          LanternIndex.clear();
          StormNetworking.clear();
          EffectRuntime.clear();
        });
    AttackBlockCallback.EVENT.register(
        (player, level, hand, pos, direction) -> {
          var state = level.getBlockState(pos);
          if (state.getBlock() instanceof SpiritLanternBlock
              && state.getValue(SpiritLanternBlock.STARTUP)) {
            player.sendOverlayMessage(Component.literal("你不能破坏一个点亮了的路灯"));
            return InteractionResult.FAIL;
          }
          return InteractionResult.PASS;
        });
    var ores = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));
    PlayerBlockBreakEvents.AFTER.register(
        (level, player, pos, state, blockEntity) -> {
          if (player instanceof ServerPlayer serverPlayer && state.is(ores))
            ProgressionService.award(
                serverPlayer,
                "ore",
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                1);
        });
    LOGGER.info(
        "Loaded Lantern in Storm: {} lamps, 35 effects, Spirit experience sources",
        StormContent.LAMPS.size());
  }
}
