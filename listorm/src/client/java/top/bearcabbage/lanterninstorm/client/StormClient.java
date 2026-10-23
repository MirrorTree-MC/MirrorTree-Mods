package top.bearcabbage.lanterninstorm.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.Identifier;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;
import top.bearcabbage.annoyingeffects.effect.*;
import top.bearcabbage.annoyingeffects.network.*;
import top.bearcabbage.lanterninstorm.content.*;
import top.bearcabbage.lanterninstorm.safety.DefenseEquipment;

public final class StormClient implements ClientModInitializer {
  private static boolean display;
  private static int stage;

  @Override
  public void onInitializeClient() {
    var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
    var container = loader.getModContainer("lantern-in-storm").orElseThrow();
    if (loader.isModLoaded("biomesoplenty"))
      net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(
          StormContent.id("bop_materials"),
          container,
          net.fabricmc.fabric.api.resource.ResourcePackActivationType.ALWAYS_ENABLED);
    if (loader.isModLoaded("mythicmetals"))
      net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(
          StormContent.id("mythic_materials"),
          container,
          net.fabricmc.fabric.api.resource.ResourcePackActivationType.ALWAYS_ENABLED);

    net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance()
        .register(
            BorderParticles.BORDER, net.minecraft.client.particle.EndRodParticle.Provider::new);
    EntityRenderers.register(
        BeginningLanternEntity.BEGINNING, c -> new BeginningLanternRenderer(c, false));
    EntityRenderers.register(
        BeginningLanternEntity.WARP, c -> new BeginningLanternRenderer(c, true));
    ClientPlayNetworking.registerGlobalReceiver(
        AnnoyingBarDisplayPayload.ID, (payload, context) -> display = payload.display());
    ClientPlayNetworking.registerGlobalReceiver(
        AnnoyingBarStagePayload.ID,
        (payload, context) -> stage = Math.clamp(payload.stage(), 0, 6));
    ClientPlayConnectionEvents.DISCONNECT.register(
        (handler, client) -> {
          display = false;
          stage = 0;
        });
    HudElementRegistry.addLast(StormContent.id("status"), StormClient::hud);
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          var player = client.player;
          if (player == null || !player.isAlive()) return;
          if (player.hasEffect(AnnoyingEffects.DISABLE_INVENTORY)
              && client.screen instanceof AbstractContainerScreen<?> screen) {
            screen.onClose();
            player.sendOverlayMessage(
                net.minecraft.network.chat.Component.translatable(
                    "messages.annoyingeffects.disable_inventory"));
          }
          var spin = player.getEffect(AnnoyingEffects.SPIN);
          if (spin != null)
            player.turn(SpinStatusEffect.ANGLE_PER_TICK * (1 + spin.getAmplifier()) / .15, 0);
          var oppressed = player.getEffect(AnnoyingEffects.OPPRESSED);
          if (oppressed != null)
            player.setXRot(
                Math.max(
                    player.getXRot(),
                    Math.min(90, OppressedStatusEffect.PITCH * (1 + oppressed.getAmplifier()))));
          if (player.hasEffect(AnnoyingEffects.CRAWLER))
            player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
        });
  }

  private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
    var client = Minecraft.getInstance();
    if (client.options.hideGui || client.player == null) return;
    if (display && stage < 6)
      graphics.blit(
          RenderPipelines.GUI_TEXTURED,
          Identifier.fromNamespaceAndPath("annoyingeffects", "textures/gui/model" + stage + ".png"),
          graphics.guiWidth() / 2 - 7,
          graphics.guiHeight() - 49,
          0,
          0,
          12,
          12,
          12,
          12);
    var stack = DefenseEquipment.find(client.player).stack();
    if (stack.isEmpty()) return;
    if ((double) stack.getDamageValue() / stack.getMaxDamage() > .9
        && client.player.tickCount % 20 < 10) return;
    String image = stack.is(StormContent.FLASHLIGHT) ? "flashlight" : "talisman";
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        StormContent.id("textures/item/" + image + ".png"),
        graphics.guiWidth() - 46,
        graphics.guiHeight() - 46,
        0,
        0,
        36,
        36,
        36,
        36);
  }
}
