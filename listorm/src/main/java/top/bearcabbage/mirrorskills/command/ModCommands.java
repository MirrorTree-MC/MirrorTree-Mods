package top.bearcabbage.mirrorskills.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.phys.Vec3;
import top.bearcabbage.mirrorskills.MirrorSkills;
import top.bearcabbage.mirrorskills.data.SkillData;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        // /mirrorskills command
        dispatcher.register(Commands.literal("mirrorskills")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) // Operator level 2

                // Grant
                .then(Commands.literal("grant")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("skill", StringArgumentType.string())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of(
                                                SkillData.SKILL_INVISIBILITY, SkillData.SKILL_DASHING, SkillData.SKILL_HEALING, SkillData.SKILL_DEATH_FLASHBACK,
                                                SkillData.SKILL_PERIODIC_ABSORPTION, SkillData.SKILL_BOW_FAST_CHARGE, SkillData.SKILL_BOW_DYNAMIC_CRIT,
                                                SkillData.SKILL_CRIT_MULTIPLIER, SkillData.SKILL_TIPPED_ARROW, SkillData.SKILL_INVINCIBLE), b))
                                        .executes(context -> setSkill(context, true)))))

                // Revoke
                .then(Commands.literal("revoke")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("skill", StringArgumentType.string())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of(
                                                SkillData.SKILL_INVISIBILITY, SkillData.SKILL_DASHING, SkillData.SKILL_HEALING,
                                                SkillData.SKILL_DEATH_FLASHBACK, SkillData.SKILL_POTION_TIME, SkillData.SKILL_POTION_LEVEL,
                                                SkillData.SKILL_PERIODIC_ABSORPTION, SkillData.SKILL_BOW_FAST_CHARGE, SkillData.SKILL_BOW_DYNAMIC_CRIT,
                                                SkillData.SKILL_CRIT_MULTIPLIER, SkillData.SKILL_TIPPED_ARROW, SkillData.SKILL_INVINCIBLE), b))
                                        .executes(context -> setSkill(context, false)))))

                // Param
                .then(Commands.literal("param")
                        .then(Commands.argument("player", EntityArgument.player())
                                // Potion Time
                                .then(Commands.literal(SkillData.SKILL_POTION_TIME)
                                        .then(Commands.argument("beneficial_add", FloatArgumentType.floatArg())
                                                .then(Commands.argument("harmful_add", FloatArgumentType.floatArg())
                                                        .executes(ModCommands::updatePotionTime))))
                                // Potion Level
                                .then(Commands.literal(SkillData.SKILL_POTION_LEVEL)
                                        .then(Commands.argument("level_add", FloatArgumentType.floatArg())
                                                .executes(ModCommands::updatePotionLevel)))
                                // Crit Multiplier
                                .then(Commands.literal("crit_multiplier")
                                        .then(Commands.argument("multiplier", FloatArgumentType.floatArg(0.0f))
                                                .executes(ModCommands::updateCritMultiplier)))
                                // Invincible Cooldown
                                .then(Commands.literal("invincible_cd")
                                        .then(Commands.argument("cooldown", FloatArgumentType.floatArg(0.0f))
                                                .executes(ModCommands::updateInvincibleCd))))));

        // /death command
        dispatcher.register(Commands.literal("death")
                .executes(ModCommands::triggerDeathFlashback));
    }

    private static int updatePotionTime(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        float beneficial = FloatArgumentType.getFloat(context, "beneficial_add");
        float harmful = FloatArgumentType.getFloat(context, "harmful_add");

        SkillData data = ((AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA).copy();
        data.setSkillEnabled(SkillData.SKILL_POTION_TIME, true);
        data.addParameter("beneficial_increase", beneficial);
        data.addParameter("harmful_reduction", harmful);

        context.getSource().sendSuccess(() -> Component.translatable("command.mirrorskills.potion_time.updated",
                player.getName().getString(), beneficial, data.getParameter("beneficial_increase"), harmful, data.getParameter("harmful_reduction")), true);
        ((AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data);
        return 1;
    }

    private static int updatePotionLevel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        float level = FloatArgumentType.getFloat(context, "level_add");

        SkillData data = ((AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA).copy();
        data.setSkillEnabled(SkillData.SKILL_POTION_LEVEL, true);
        data.addParameter("level_adjustment", level);

        context.getSource().sendSuccess(() -> Component.translatable("command.mirrorskills.potion_level.updated",
                player.getName().getString(), level, data.getParameter("level_adjustment")), true);
        ((AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data);
        return 1;
    }

    private static int updateCritMultiplier(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        float multiplier = FloatArgumentType.getFloat(context, "multiplier");

        SkillData data = ((AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA).copy();
        data.setSkillEnabled(SkillData.SKILL_CRIT_MULTIPLIER, true);
        data.setParameter("crit_multiplier", multiplier);

        context.getSource().sendSuccess(() -> Component.translatable("command.mirrorskills.crit_multiplier.updated",
                player.getName().getString(), multiplier, data.getParameter("crit_multiplier")), true);
        ((AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data);
        return 1;
    }

    private static int updateInvincibleCd(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        float cooldown = FloatArgumentType.getFloat(context, "cooldown");

        SkillData data = ((AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA).copy();
        data.setParameter("invincible_cd", cooldown);

        context.getSource().sendSuccess(() -> Component.translatable("command.mirrorskills.invincible_cd.updated",
                player.getName().getString(), cooldown), true);
        ((AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data);
        return 1;
    }

    private static int setSkill(CommandContext<CommandSourceStack> context, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String skill = StringArgumentType.getString(context, "skill");
        SkillData data = ((AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA).copy();
        data.setSkillEnabled(skill, enabled);
        if (enabled) {
            if (SkillData.SKILL_PERIODIC_ABSORPTION.equals(skill))
                data.setParameter("periodic_timer", 12000.0f);
            ((AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data);
            context.getSource().sendSuccess(() -> Component.translatable("command.mirrorskills.grant.success", skill, player.getName().getString()), true);
        } else {
            var parameters = switch (skill) {
                case SkillData.SKILL_POTION_TIME -> List.of("beneficial_increase", "harmful_reduction");
                case SkillData.SKILL_POTION_LEVEL -> List.of("level_adjustment");
                case SkillData.SKILL_CRIT_MULTIPLIER -> List.of("crit_multiplier");
                case SkillData.SKILL_PERIODIC_ABSORPTION -> List.of("periodic_timer");
                default -> List.<String>of();
            };
            for (String parameter : parameters) data.setParameter(parameter, 0f);
            context.getSource().sendSuccess(() -> Component.translatable(parameters.isEmpty()
                    ? "command.mirrorskills.revoke.success" : "command.mirrorskills.revoke.reset", skill), true);
            ((AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, data);
        }
        return 1;
    }

    private static int triggerDeathFlashback(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        SkillData data = ((AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA);

        if (!data.isSkillEnabled(SkillData.SKILL_DEATH_FLASHBACK)) {
            context.getSource().sendSuccess(() -> Component.translatable("command.mirrorskills.death.no_skill"), false);
            return 0;
        }

        if (data.hasDeathLocation()) {
            Vec3 loc = data.getLastDeathLocation();

            String dimStr = data.getLastDeathDimension();
            // Legacy records without a dimension retain their current-world behavior.
            ServerLevel targetWorld = player.level();
            if (dimStr != null) {
                Identifier dimId = Identifier.tryParse(dimStr);
                targetWorld = dimId == null || dimStr.isEmpty() ? null : context.getSource().getServer()
                        .getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
                if (targetWorld == null) {
                    context.getSource().sendFailure(Component.translatable("command.mirrorskills.death.dimension_unavailable"));
                    return 0;
                }
            }
            if (!Double.isFinite(loc.x) || !Double.isFinite(loc.y) || !Double.isFinite(loc.z)) {
                context.getSource().sendFailure(Component.translatable("command.mirrorskills.death.invalid_location"));
                return 0;
            }
            if (!player.teleportTo(targetWorld, loc.x, loc.y, loc.z, Set.of(), player.getYRot(), player.getXRot(), true)) {
                context.getSource().sendFailure(Component.translatable("command.mirrorskills.death.failed"));
                return 0;
            }

            SkillData updated = ((AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA).copy();
            updated.clearDeathLocation(); // Consume only after a successful teleport.
            ((AttachmentTarget) player).setAttached(MirrorSkills.SKILL_DATA, updated);
            context.getSource().sendSuccess(() -> Component.translatable("command.mirrorskills.death.success"), false);
            return 1;
        } else {
            context.getSource().sendSuccess(() -> Component.translatable("command.mirrorskills.death.no_location"), false);
            return 0;
        }
    }
}
