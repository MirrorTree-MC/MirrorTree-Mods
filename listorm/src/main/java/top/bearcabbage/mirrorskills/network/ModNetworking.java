package top.bearcabbage.mirrorskills.network;

import java.util.Random;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;
import top.bearcabbage.mirrorskills.MirrorSkills;
import top.bearcabbage.mirrorskills.data.SkillData;

public final class ModNetworking {
    public static final CustomPacketPayload.Type<TriggerSkillPayload> TRIGGER_SKILL_ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MirrorSkills.MOD_ID, "trigger_skill"));

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TRIGGER_SKILL_ID, TriggerSkillPayload.CODEC);
        // Fabric invokes play payload handlers on the server thread.
        ServerPlayNetworking.registerGlobalReceiver(TRIGGER_SKILL_ID,
                (payload, context) -> executeSkill(context.player(), payload.skillId()));
    }

    private static void executeSkill(ServerPlayer player, String skillId) {
        SkillData data = ((AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA);
        if (!data.isSkillEnabled(skillId)) return;

        long currentTime = player.level().getGameTime();
        long lastTime = data.getCooldown(skillId);

        if (skillId.equals(SkillData.SKILL_INVISIBILITY)) {
            if (lastTime != 0 && currentTime - lastTime < 1200) {
                long remainingSeconds = (1200 - (currentTime - lastTime)) / 20;
                player.sendSystemMessage(Component.translatable("message.mirrorskills.cooldown", remainingSeconds), true);
                return;
            }
            player.addEffect(new MobEffectInstance(
                    MobEffects.INVISIBILITY,
                    5 * 20,
                    0,
                    false,
                    false // no particles
            ));

            player.level().sendParticles(
                ParticleTypes.POOF,
                player.getX(), player.getY() + 1.0, player.getZ(),
                10, // count
                0.5, 0.5, 0.5, // delta
                0.05 // speed
            );

            data.setCooldown(skillId, currentTime);
            ((AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy());
            player.sendSystemMessage(Component.translatable("message.mirrorskills.invisibility_activated"), true);
        } else if (skillId.equals(SkillData.SKILL_DASHING)) {
            if (lastTime != 0 && currentTime - lastTime < 20) return;

            Vec3 vec = player.getLookAngle();
            player.push(vec.x * 2.0, 0.4, vec.z * 2.0); // Slight vertical boost to reduce friction issues
            player.hurtMarked = true; // Mark velocity as modified
            player.connection.send(new ClientboundSetEntityMotionPacket(player));

            data.setCooldown(skillId, currentTime);
            ((AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy());
        } else if (SkillData.SKILL_INVINCIBLE.equals(skillId)) {
            float cooldownSeconds = data.getParameter("invincible_cd");
            if (cooldownSeconds <= 0) cooldownSeconds = 120.0f;

            if (lastTime != 0 && currentTime - lastTime < (long)(cooldownSeconds * 20)) {
                long remainingSeconds = ((long)(cooldownSeconds * 20) - (currentTime - lastTime)) / 20;
                player.sendSystemMessage(Component.translatable("message.mirrorskills.cooldown", remainingSeconds), true);
                return;
            }

            data.setParameter("invincible_ticks", 100.0f);

            data.setCooldown(SkillData.SKILL_INVINCIBLE, currentTime);
            ((AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy());
            player.sendSystemMessage(Component.translatable("message.mirrorskills.invincible_activated"), true);
        } else if (SkillData.SKILL_TIPPED_ARROW.equals(skillId)) {
            if (lastTime != 0 && currentTime - lastTime < 6000) {
                long remainingSeconds = (6000 - (currentTime - lastTime)) / 20;
                player.sendSystemMessage(Component.translatable("message.mirrorskills.cooldown", remainingSeconds), true);
                return;
            }

            giveRandomTippedArrows(player);
            data.setCooldown(SkillData.SKILL_TIPPED_ARROW, currentTime);
            ((AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data.copy());
            player.sendSystemMessage(Component.translatable("message.mirrorskills.tipped_arrow_activated"), true);
        }
    }

    private static void giveRandomTippedArrows(ServerPlayer player) {
        Random random = new Random();
        var potions = BuiltInRegistries.POTION.keySet().stream().toList();
        if (potions.isEmpty()) return;

        Identifier randomPotionId = potions.get(random.nextInt(potions.size()));
        var potionEntry = BuiltInRegistries.POTION.get(randomPotionId).orElseThrow();

        ItemStack arrowStack = new ItemStack(Items.TIPPED_ARROW, 16);
        arrowStack.set(DataComponents.POTION_CONTENTS, new PotionContents(potionEntry));

        if (!player.getInventory().add(arrowStack)) {
            player.drop(arrowStack, false);
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
