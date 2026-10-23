package top.bearcabbage.mirrorskills.skill;

import java.util.ArrayList;
import java.util.Collection;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import top.bearcabbage.mirrorskills.MirrorSkills;
import top.bearcabbage.mirrorskills.data.SkillData;

public class SkillHandler {

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            SkillData data = ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA);
            
            // Critical Hit Multiplier Cap
            if (data.isSkillEnabled(SkillData.SKILL_CRIT_MULTIPLIER)) {
                float currentMult = data.getParameter("crit_multiplier");
                if (currentMult > 2.0f) {
                    data.setParameter("crit_multiplier", 2.0f);
                    ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy());
                    MirrorSkills.LOGGER.info("Capped critical hit multiplier for {} to 2.0", player.getName().getString());
                }
            }
            
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                SkillData data = ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA);
                if (data.isSkillEnabled(SkillData.SKILL_DEATH_FLASHBACK)) {
                    // data.setLastDeathLocation(player.position()); now requires dimension
                    String dim = player.level().dimension().identifier().toString();
                    data.setLastDeathLocation(player.position(), dim);
                    ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy());
                }
            }
        });

        ServerTickEvents.END_LEVEL_TICK.register(world -> {
            for (net.minecraft.server.level.ServerPlayer player : world.players()) {
                SkillData skillData = ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA);
                tickHealing(player, skillData, world.getGameTime());
                tickPeriodicAbsorption(player, skillData);
                tickInvincible(player, skillData);
            }
        });
    }

    private static void tickHealing(ServerPlayer player, SkillData data, long currentTime) {
        if (!data.isSkillEnabled(SkillData.SKILL_HEALING)) return;

        // "regenerate 10 health point when a player's health is <= 6 autometically, cd 10min"
        
        long lastTime = data.getCooldown(SkillData.SKILL_HEALING);
        long cooldownTime = 10 * 60 * 20; // 10 minutes in ticks

        if (lastTime != 0 && currentTime - lastTime < cooldownTime) return;

        if (player.getHealth() <= 6.0f && player.getHealth() > 0) { // Don't heal dead players
            player.heal(10.0f);
            data.setCooldown(SkillData.SKILL_HEALING, currentTime);
            
            // "add some particles at the player position to visualize"
            ServerLevel world = (ServerLevel) player.level();
            world.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1, player.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
            player.sendSystemMessage(Component.translatable("message.mirrorskills.healing_activated"), true);
            ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy()); // FORCE COPY
        }
    }

    private static void tickPeriodicAbsorption(ServerPlayer player, SkillData data) {
        if (data.isSkillEnabled(SkillData.SKILL_PERIODIC_ABSORPTION)) {
            // Using a parameter as a timer: "periodic_timer"
            // Increment every tick. Max 12000 (10 mins).
            float timer = data.getParameter("periodic_timer");
            timer += 1.0f;
            
            if (timer >= 12000) {
                timer = 0;
                // Update parameter BEFORE syncing
                data.setParameter("periodic_timer", timer);
                
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 0));
                MirrorSkills.LOGGER.info("Periodic Absorption granted to {}", player.getName().getString());
                // Sync only on activation/reset. FORCE COPY to ensure dirty mark.
                ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy()); 
            } else {
                // Manually update param without "addParameter" which adds to existing.
                // setParameter is needed.
                data.setParameter("periodic_timer", timer);
                ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy());
                // Do NOT sync every tick for periodic - wasteful and not needed for client.
            }
        }
    }

    private static void tickInvincible(ServerPlayer player, SkillData data) {
        // Invincible active time
        float activeTicks = data.getParameter("invincible_ticks");
        if (activeTicks > 0) {
            activeTicks -= 1.0f;
            data.setParameter("invincible_ticks", activeTicks);
            ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy()); // Sync every tick for smooth HUD & persistence
        }
    }
}
