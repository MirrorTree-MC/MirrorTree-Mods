package top.bearcabbage.mirrortree.validation;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.ChannelInfoHolder;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

/** Embedded client for the isolated probe, with the installed mods' play payload channels. */
public final class ProbePlayers {
    private ProbePlayers() {}

    @SuppressWarnings("unchecked")
    public static ServerPlayer join(MinecraftServer server, String name) throws ReflectiveOperationException {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        var player = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        // The probe skips configuration. Preserve its normal client channel declaration before play starts.
        var registry = PayloadTypeRegistry.clientboundPlay();
        var packetTypes = registry.getClass().getDeclaredField("packetTypes");
        packetTypes.setAccessible(true);
        var channels = Set.copyOf(((Map<Identifier, ?>) packetTypes.get(registry)).keySet());
        ((ChannelInfoHolder) connection).fabric_getPendingChannelsNames(ConnectionProtocol.PLAY).addAll(channels);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        if (player.connection == null || !connection.isConnected() || player.hasDisconnected())
            throw new AssertionError("Embedded client disconnected while joining: " + name);
        if (!ServerPlayNetworking.getSendable(player).containsAll(channels))
            throw new AssertionError("Embedded client play payload registration is incomplete: " + name);
        return player;
    }
}
