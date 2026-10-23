package top.bearcabbage.mirrorskills.client;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import top.bearcabbage.mirrorskills.MirrorSkills;
import top.bearcabbage.mirrorskills.data.SkillData;
import top.bearcabbage.mirrorskills.network.ModNetworking;
public class MirrorSkillsClient implements ClientModInitializer {
 public void onInitializeClient() {
  var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("mirrorskills","general"));
  var invisible=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mirrorskills.invisible",GLFW.GLFW_KEY_I,category));
  var dash=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mirrorskills.dash",GLFW.GLFW_KEY_O,category));
  var invincible=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mirrorskills.invincible",GLFW.GLFW_KEY_GRAVE_ACCENT,category));
  ClientTickEvents.END_CLIENT_TICK.register(client -> {
   if(client.player==null || !ClientPlayNetworking.canSend(ModNetworking.TRIGGER_SKILL_ID)) return;
   while(invisible.consumeClick()) ClientPlayNetworking.send(new ModNetworking.TriggerSkillPayload(SkillData.SKILL_INVISIBILITY));
   while(dash.consumeClick()) ClientPlayNetworking.send(new ModNetworking.TriggerSkillPayload(SkillData.SKILL_DASHING));
   while(invincible.consumeClick()) ClientPlayNetworking.send(new ModNetworking.TriggerSkillPayload(SkillData.SKILL_INVINCIBLE));
  });
  HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,Identifier.fromNamespaceAndPath("mirrorskills","countdown"),(graphics,delta)->{
   var client=Minecraft.getInstance(); if(client.player==null)return;
   var data=((AttachmentTarget)client.player).getAttached(MirrorSkills.SKILL_DATA);
   if(data!=null && data.getParameter("invincible_ticks")>0) graphics.centeredText(client.font,String.format(java.util.Locale.ROOT,"%.1fs",data.getParameter("invincible_ticks")/20.0f),graphics.guiWidth()/2,graphics.guiHeight()-65,0xFFFFAA00);
  });
 }
}
