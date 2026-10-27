package top.bearcabbage.minesweeper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.ScoreHolder;

public final class MinesweeperBridge implements ModInitializer {
    private static final Identifier SESSION = Identifier.parse("minesweeper:session/run");
    private static final Map<UUID, Integer> LAST_ACTION = new HashMap<>();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                dispatcher.register(Commands.literal("minesweeper").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!ready(context.getSource().getServer())) {
                        context.getSource().sendFailure(Component.literal("Minesweeper datapack is not enabled."));
                        return 0;
                    }
                    if (!takeTurn(player)) return 0;
                    run(player, "function minesweeper:session/run {task:\"play_minesweeper\"}");
                    return 1;
                })));
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            var objective = server.getScoreboard().getObjective("minesweeper.const");
            if (objective != null && ready(server)) {
                server.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(".bridge"), objective).set(1);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_ACTION.remove(handler.player.getUUID()));
    }

    private static boolean ready(MinecraftServer server) {
        return server.getFunctions().get(SESSION).isPresent();
    }

    private static boolean takeTurn(ServerPlayer player) {
        int tick = player.level().getServer().getTickCount();
        Integer previous = LAST_ACTION.put(player.getUUID(), tick);
        return previous == null || previous != tick;
    }

    public static boolean handle(ServerPlayer player, ServerboundCustomClickActionPacket packet) {
        Identifier id = packet.id();
        if (!id.equals(Identifier.parse("minesweeper:cell"))
                && !id.equals(Identifier.parse("minesweeper:action"))
                && !id.equals(Identifier.parse("minesweeper:settings"))) return false;
        if (!(packet.payload().orElse(null) instanceof CompoundTag payload)
                || !player.entityTags().contains("minesweeper.in_game")) return true;
        int code = -1;
        if (id.getPath().equals("cell")) {
            int index = payload.getInt("index").orElse(-1);
            if (index >= 0 && index < 480) code = index + 1;
        } else if (id.getPath().equals("action")) {
            int value = payload.getInt("code").orElse(-1);
            if ((value >= 901 && value <= 909) || (value >= 911 && value <= 913) || value == 999) code = value;
        } else if (player.entityTags().contains("minesweeper.in_settings")) {
            int size = switch (payload.getString("size").orElse("")) {
                case "1" -> 1; case "2" -> 2; case "3" -> 3; default -> 0;
            };
            int density = switch (payload.getString("mine_count").orElse("")) {
                case "10" -> 10; case "16" -> 16; case "24" -> 24; default -> 0;
            };
            if (size != 0 && density != 0) code = size * 100000 + density * 1000;
        }
        MinecraftServer server = player.level().getServer();
        var objective = server.getScoreboard().getObjective("minesweeper.click");
        if (code < 0 || objective == null || !ready(server) || !takeTurn(player)) return true;
        // Menus and exit do not change the board: avoid loading/copying the personal snapshot.
        if (code == 902) run(player, "function minesweeper:settings/show/do");
        else if (code == 904) run(player, "function minesweeper:leave_game");
        else {
            server.getScoreboard().getOrCreatePlayerScore(player, objective).set(code);
            run(player, "function minesweeper:session/run {task:\"handle_input\"}");
        }
        return true;
    }

    private static void run(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        var source = server.createCommandSourceStack().withEntity(player).withLevel(player.level())
                .withPosition(player.position()).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }
}
