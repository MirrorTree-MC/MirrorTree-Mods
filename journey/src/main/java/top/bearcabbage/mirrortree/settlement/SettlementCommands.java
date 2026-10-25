package top.bearcabbage.mirrortree.settlement;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import top.bearcabbage.lanterninstorm.content.BeginningLanternEntity;
import top.bearcabbage.mirrortree.MirrorTree;

public final class SettlementCommands {
    private static final String MANAGED = "mirrortree_settlement";
    private static final String LANTERN_PREFIX = "聚落入梦点[";
    private static MinecraftServer initialized;

    private SettlementCommands() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(Commands.literal("mtwarp")
                    .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.argument("name", StringArgumentType.greedyString()).executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        requireIntegration();
                        if (!player.level().dimension().equals(Level.OVERWORLD))
                            throw error("请在主世界站到聚落入梦位置，再执行 /mtwarp 名称。");
                        String name = fullName(StringArgumentType.getString(context, "name"));
                        if (SettlementWarps.exists(name)) throw error("这个聚落已存在；请先用 /mtwarp-delete 删除。");
                        if (!player.level().getWorldBorder().isWithinBounds(player.blockPosition()))
                            throw error("聚落位置超出世界边界。");
                        if (player.getY() <= player.level().getMinY() || player.getY() + 1 >= player.level().getMaxY())
                            throw error("聚落位置超出世界高度范围。");
                        SettlementWarps.create(player, name);
                        // The warp is persisted before adding its managed marker.
                        var lantern = new BeginningLanternEntity(BeginningLanternEntity.WARP, player.level());
                        lantern.setPos(Vec3.atCenterOf(player.blockPosition()));
                        lantern.setCustomName(Component.literal(lanternName(name)));
                        lantern.addTag(MANAGED);
                        if (!player.level().addFreshEntity(lantern)) {
                            SettlementWarps.delete(name);
                            throw error("无法创建聚落灯笼，已撤销这次聚落创建。");
                        }
                        context.getSource().sendSuccess(() -> Component.literal("已创建 " + name + "；首次入梦的玩家可在卧室床边选择加入。"), true);
                        return 1;
                    })));
            dispatcher.register(Commands.literal("mtwarp-delete")
                    .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                            .suggests((context, builder) -> {
                                var player = context.getSource().getPlayer();
                                return SharedSuggestionProvider.suggest(player == null ? java.util.List.of()
                                        : SettlementWarps.accessible(player).stream()
                                                .map(w -> StringArgumentType.escapeIfRequired(w.displayName())).toList(), builder);
                            }).executes(context -> {
                                requireIntegration();
                                String name = fullName(StringArgumentType.getString(context, "name"));
                                if (!SettlementWarps.delete(name)) throw error("没有找到这个聚落。");
                                var world = context.getSource().getServer().overworld();
                                for (var entity : world.getEntities(BeginningLanternEntity.WARP,
                                        e -> e.hasCustomName() && e.getCustomName().getString().equals(lanternName(name))))
                                    entity.discard();
                                context.getSource().sendSuccess(() -> Component.literal("已删除 " + name
                                        + "。其未加载的聚落灯笼将在加载时清理。"), true);
                                return 1;
                            })));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> initialized = null);
        // Defer until after every mod has completed SERVER_STARTED and loaded its warp data.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (initialized == server) return;
            initialized = server;
            for (var entity : server.overworld().getEntities(BeginningLanternEntity.WARP, e -> e.entityTags().contains(MANAGED)))
                prune(entity);
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (initialized == world.getServer() && entity instanceof BeginningLanternEntity lantern) prune(lantern);
        });
    }

    private static void prune(BeginningLanternEntity entity) {
        if (!SettlementWarps.available() || entity.getType() != BeginningLanternEntity.WARP
                || !entity.level().dimension().equals(Level.OVERWORLD) || !entity.entityTags().contains(MANAGED)) return;
        String label = entity.hasCustomName() ? entity.getCustomName().getString() : "";
        if (!label.startsWith(LANTERN_PREFIX) || !label.endsWith("]")) return;
        String name = SettlementWarps.PREFIX + label.substring(LANTERN_PREFIX.length(), label.length() - 1);
        try {
            if (!SettlementWarps.exists(name)) entity.discard();
        } catch (RuntimeException e) { MirrorTree.LOGGER.warn("Cannot verify settlement lantern {}", name, e); }
    }

    private static String lanternName(String name) { return LANTERN_PREFIX + name.substring(SettlementWarps.PREFIX.length()) + "]"; }

    private static void requireIntegration() throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!SettlementWarps.available()) throw error("聚落功能需要服务端安装 Essential Commands；随机入梦仍可使用。");
    }

    private static String fullName(String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        name = name.strip();
        if (!name.isEmpty() && com.mojang.brigadier.StringReader.isQuotedStringStart(name.charAt(0))) {
            var reader = new com.mojang.brigadier.StringReader(name);
            name = reader.readString();
            reader.skipWhitespace();
            if (reader.canRead()) throw error("引号后有多余内容，请把完整聚落名称放在同一组引号中。");
        }
        if (name.startsWith(SettlementWarps.PREFIX)) name = name.substring(SettlementWarps.PREFIX.length()).strip();
        if (name.isBlank() || name.codePointCount(0, name.length()) > 64 || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '§'))
            throw error("聚落名称需为 1～64 个字符，不能包含控制符或颜色代码。");
        return SettlementWarps.PREFIX + name;
    }

    private static com.mojang.brigadier.exceptions.CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }
}
