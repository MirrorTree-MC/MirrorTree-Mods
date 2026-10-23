package top.bearcabbage.mirrorskills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

public class MirrorSkills implements ModInitializer {
	public static final String MOD_ID = "mirrorskills";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<top.bearcabbage.mirrorskills.data.SkillData> SKILL_DATA = net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.<top.bearcabbage.mirrorskills.data.SkillData>builder()
			.persistent(top.bearcabbage.mirrorskills.data.SkillData.CODEC)
			.initializer(top.bearcabbage.mirrorskills.data.SkillData::new)
			.copyOnDeath()
			.syncWith(net.minecraft.network.codec.ByteBufCodecs.fromCodec(top.bearcabbage.mirrorskills.data.SkillData.CODEC).cast(), net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all())
			.buildAndRegister(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "skill_data"));

	@Override
	public void onInitialize() {
		// Register Commands
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(top.bearcabbage.mirrorskills.command.ModCommands::register);

		// Register Event Handlers
		top.bearcabbage.mirrorskills.skill.SkillHandler.register();

		// Register Networking
		top.bearcabbage.mirrorskills.network.ModNetworking.registerCommon();
		top.bearcabbage.mirrorskills.network.ModNetworking.registerServer();

		LOGGER.info("MirrorSkills initialized!");
	}
}