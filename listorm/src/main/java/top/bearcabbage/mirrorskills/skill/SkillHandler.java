package top.bearcabbage.mirrorskills.skill;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
            SkillData data = ((AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA);

            // Critical Hit Multiplier Cap
            if (data.isSkillEnabled(SkillData.SKILL_CRIT_MULTIPLIER)) {
                float currentMult = data.getParameter("crit_multiplier");
                if (currentMult > 2.0f) {
                    data.setParameter("crit_multiplier", 2.0f);
                    ((AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy());
                    MirrorSkills.LOGGER.info("Capped critical hit multiplier for {} to 2.0", player.getName().getString());
                }
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                SkillData data = ((AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA);
                if (data.isSkillEnabled(SkillData.SKILL_DEATH_FLASHBACK)) {
                    String dim = player.level().dimension().identifier().toString();
                    data.setLastDeathLocation(player.position(), dim);
                    ((AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy());
                }
            }
        });

        ServerTickEvents.END_LEVEL_TICK.register(world -> {
            long currentTime = world.getGameTime();
            for (ServerPlayer player : world.players()) {
                AttachmentTarget target = (AttachmentTarget) player;
                SkillData skillData = target.getAttachedOrCreate(MirrorSkills.SKILL_DATA);
                boolean changed = tickHealing(player, skillData, currentTime);
                changed |= tickPeriodicAbsorption(player, skillData);
                changed |= tickInvincible(skillData);
                if (changed) {
                    // A new instance marks the attachment dirty and publishes the final tick state.
                    target.setAttached(MirrorSkills.SKILL_DATA, skillData.copy());
                }
            }
        });
    }

    private static boolean tickHealing(ServerPlayer player, SkillData data, long currentTime) {
        if (!data.isSkillEnabled(SkillData.SKILL_HEALING)) return false;

        long lastTime = data.getCooldown(SkillData.SKILL_HEALING);
        long cooldownTime = 10 * 60 * 20; // 10 minutes in ticks

        if (lastTime != 0 && currentTime - lastTime < cooldownTime) return false;

        if (player.getHealth() <= 6.0f && player.getHealth() > 0) { // Don't heal dead players
            player.heal(10.0f);
            data.setCooldown(SkillData.SKILL_HEALING, currentTime);

            ServerLevel world = player.level();
            world.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1, player.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
            player.sendSystemMessage(Component.translatable("message.mirrorskills.healing_activated"), true);
            return true;
        }
        return false;
    }

    private static boolean tickPeriodicAbsorption(ServerPlayer player, SkillData data) {
        if (!data.isSkillEnabled(SkillData.SKILL_PERIODIC_ABSORPTION)) return false;

        float timer = data.getParameter("periodic_timer") + 1.0f;
        if (timer >= 12000) {
            data.setParameter("periodic_timer", 0);
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, MobEffectInstance.INFINITE_DURATION, 0));
            MirrorSkills.LOGGER.info("Periodic Absorption granted to {}", player.getName().getString());
        } else {
            data.setParameter("periodic_timer", timer);
        }
        return true;
    }

    private static boolean tickInvincible(SkillData data) {
        float activeTicks = data.getParameter("invincible_ticks");
        if (activeTicks > 0) {
            data.setParameter("invincible_ticks", activeTicks - 1.0f);
            return true;
        }
        return false;
    }
}
