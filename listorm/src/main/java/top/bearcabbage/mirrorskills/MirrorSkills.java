package top.bearcabbage.mirrorskills;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.bearcabbage.mirrorskills.command.ModCommands;
import top.bearcabbage.mirrorskills.data.SkillData;
import top.bearcabbage.mirrorskills.network.ModNetworking;
import top.bearcabbage.mirrorskills.skill.SkillHandler;

public final class MirrorSkills {
    public static final String MOD_ID = "mirrorskills";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final AttachmentType<SkillData> SKILL_DATA = AttachmentRegistry.<SkillData>builder()
            .persistent(SkillData.CODEC)
            .initializer(SkillData::new)
            .copyOnDeath()
            .syncWith(ByteBufCodecs.fromCodec(SkillData.CODEC).cast(), AttachmentSyncPredicate.all())
            .buildAndRegister(Identifier.fromNamespaceAndPath(MOD_ID, "skill_data"));

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register(ModCommands::register);
        SkillHandler.register();
        ModNetworking.register();
        LOGGER.info("MirrorSkills initialized!");
    }
}
