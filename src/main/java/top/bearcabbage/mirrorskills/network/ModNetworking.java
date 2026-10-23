package top.bearcabbage.mirrorskills.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import top.bearcabbage.mirrorskills.MirrorSkills;
import top.bearcabbage.mirrorskills.data.SkillData;

public class ModNetworking {
    public static final CustomPacketPayload.Type<TriggerSkillPayload> TRIGGER_SKILL_ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MirrorSkills.MOD_ID, "trigger_skill"));

    public static void registerCommon() {
        PayloadTypeRegistry.serverboundPlay().register(TRIGGER_SKILL_ID, TriggerSkillPayload.CODEC);
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(TRIGGER_SKILL_ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                String skillId = payload.skillId();
                executeSkill(player, skillId);
            });
        });
    }

    private static void executeSkill(ServerPlayer player, String skillId) {
        SkillData data = ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA);
        if (!data.isSkillEnabled(skillId)) return;

        long currentTime = player.level().getGameTime();
        long lastTime = data.getCooldown(skillId);
        
        // Skill 1: Invisibility
        if (skillId.equals(SkillData.SKILL_INVISIBILITY)) {
            // cd 60s
            // If lastTime is 0, it means never used, so it should be allowed.
            if (lastTime != 0 && currentTime - lastTime < 60 * 20) {
                 long remainingSeconds = (60 * 20 - (currentTime - lastTime)) / 20;
                 player.sendSystemMessage(Component.translatable("message.mirrorskills.cooldown", remainingSeconds), true);
                 return;
            }
            // "invisibility without effect particles for 5s"
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.INVISIBILITY,
                    5 * 20,
                    0,
                    false,
                    false // no particles
            ));
            
            // Spawn Particles (Poof)
            ((net.minecraft.server.level.ServerLevel) player.level()).sendParticles(
                net.minecraft.core.particles.ParticleTypes.POOF,
                player.getX(), player.getY() + 1.0, player.getZ(),
                10, // count
                0.5, 0.5, 0.5, // delta
                0.05 // speed
            );

            data.setCooldown(skillId, currentTime);
            ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy()); // FORCE COPY
            player.sendSystemMessage(Component.translatable("message.mirrorskills.invisibility_activated"), true);
        }
        
        // Skill 2: Dashing
        else if (skillId.equals(SkillData.SKILL_DASHING)) {
            // cd 1s
            if (lastTime != 0 && currentTime - lastTime < 1 * 20) return;
            
            // "move forward for 5blocks very fast ... Do not use teleport"
            // Use push instead of setVelocity for better compatibility.
            // 3.0 might be too fast, let's try 2.0 which is substantial.
            
            Vec3 vec = player.getLookAngle();
            player.push(vec.x * 2.0, 0.4, vec.z * 2.0); // Slight vertical boost to reduce friction issues
            player.hurtMarked = true; // Mark velocity as modified
            // Sync velocity to client explicitly if needed, but push usually handles it if server-side movement is authoritative.
            // Actually, for instant burst, explicit packet helps smoothness.
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
            
            data.setCooldown(skillId, currentTime);
            ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy()); // FORCE COPY
            // No message for dash usually, or maybe "Dash!"
        }
        else if (SkillData.SKILL_INVINCIBLE.equals(skillId)) {
            // Invincible Trigger (if enabled)
            if (!data.isSkillEnabled(SkillData.SKILL_INVINCIBLE)) return;
            
            // Check cooldown (using separate timestamps for consistency with other active skills)
            long invincibleLastTime = data.getCooldown(SkillData.SKILL_INVINCIBLE);
            
            // Default cooldown 120s = 2400 ticks. Check if "invincible_cd" param exists.
            float cooldownSeconds = data.getParameter("invincible_cd");
            if (cooldownSeconds <= 0) cooldownSeconds = 120.0f; // Default 120s

            if (invincibleLastTime != 0 && currentTime - invincibleLastTime < (long)(cooldownSeconds * 20)) {
                 long remainingSeconds = ((long)(cooldownSeconds * 20) - (currentTime - invincibleLastTime)) / 20;
                 player.sendSystemMessage(Component.translatable("message.mirrorskills.cooldown", remainingSeconds), true);
                 return;
            }
            
            // Activate: Set invincible_ticks to 60 (3s)
            data.setParameter("invincible_ticks", 100.0f);
            
            data.setCooldown(SkillData.SKILL_INVINCIBLE, currentTime);
            ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy()); // FORCE COPY
            player.sendSystemMessage(Component.translatable("message.mirrorskills.invincible_activated"), true);
        }
        else if (SkillData.SKILL_TIPPED_ARROW.equals(skillId)) {
             // Tipped Arrow Trigger
             if (!data.isSkillEnabled(SkillData.SKILL_TIPPED_ARROW)) return;
             
             long arrowLastTime = data.getCooldown(SkillData.SKILL_TIPPED_ARROW);
             // Cooldown 300s = 6000 ticks
             if (arrowLastTime != 0 && currentTime - arrowLastTime < 300 * 20) {
                 long remainingSeconds = (300 * 20 - (currentTime - arrowLastTime)) / 20;
                 player.sendSystemMessage(Component.translatable("message.mirrorskills.cooldown", remainingSeconds), true);
                 return;
             }
             
             giveRandomTippedArrows(player);
             data.setCooldown(SkillData.SKILL_TIPPED_ARROW, currentTime);
             ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy()); // FORCE COPY
             player.sendSystemMessage(Component.translatable("message.mirrorskills.tipped_arrow_activated"), true);
        }
    }

    private static void giveRandomTippedArrows(net.minecraft.server.level.ServerPlayer player) {
        java.util.Random random = new java.util.Random();
        var potions = net.minecraft.core.registries.BuiltInRegistries.POTION.keySet().stream().toList();
        if (potions.isEmpty()) return;

        net.minecraft.resources.Identifier randomPotionId = potions.get(random.nextInt(potions.size()));
        var potionEntry = net.minecraft.core.registries.BuiltInRegistries.POTION.get(randomPotionId).orElseThrow();
        
        if (potionEntry != null) {
            net.minecraft.world.item.ItemStack arrowStack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TIPPED_ARROW, 16);
            arrowStack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new net.minecraft.world.item.alchemy.PotionContents(potionEntry));
            
            if (!player.getInventory().add(arrowStack)) {
                player.drop(arrowStack, false);
            }
        }
    }

    public record TriggerSkillPayload(String skillId) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, TriggerSkillPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, TriggerSkillPayload::skillId,
                TriggerSkillPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TRIGGER_SKILL_ID;
        }
    }
}
