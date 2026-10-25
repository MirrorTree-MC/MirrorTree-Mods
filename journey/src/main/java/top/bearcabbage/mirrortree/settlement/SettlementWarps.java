package top.bearcabbage.mirrortree.settlement;

import java.util.Comparator;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Optional Essential Commands integration; no copied warp database or client-side trust. */
public final class SettlementWarps {
    public static final String PREFIX = "聚落：";
    public record Settlement(String name, double x, double y, double z, float yaw, float pitch) {
        public String displayName() { return name.substring(PREFIX.length()); }
        public BlockPos blockPos() { return BlockPos.containing(x, y, z); }
    }

    private SettlementWarps() {}

    public static boolean available() { return FabricLoader.getInstance().isModLoaded("essential_commands"); }

    public static List<Settlement> accessible(ServerPlayer player) {
        return available() ? EssentialAccess.accessible(player) : List.of();
    }

    public static Settlement resolve(ServerPlayer player, String name) {
        return available() ? EssentialAccess.resolve(player, name) : null;
    }

    public static boolean exists(String name) { return available() && EssentialAccess.exists(name); }

    public static void create(ServerPlayer player, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        EssentialAccess.create(player, name);
    }

    public static boolean delete(String name) { return EssentialAccess.delete(name); }

    // Kept in a separate class so servers and clients without this optional mod can load Journey.
    private static final class EssentialAccess {
        private static com.fibermc.essentialcommands.WorldDataManager manager() {
            var manager = com.fibermc.essentialcommands.ManagerLocator.getInstance().getWorldDataManager();
            if (manager == null) throw new IllegalStateException("Essential Commands 尚未完成加载");
            return manager;
        }

        static List<Settlement> accessible(ServerPlayer player) {
            return manager().getAccessibleWarps(player)
                    .filter(EssentialAccess::isSettlement).map(EssentialAccess::point)
                    .sorted(Comparator.comparing(Settlement::name)).toList();
        }

        static Settlement resolve(ServerPlayer player, String name) {
            var warp = manager().getWarp(name);
            return isSettlement(warp) && warp.hasPermission(player) ? point(warp) : null;
        }

        private static boolean isSettlement(com.fibermc.essentialcommands.types.WarpLocation warp) {
            return warp != null && warp.getName().startsWith(PREFIX) && warp.getName().length() > PREFIX.length()
                    && Level.OVERWORLD.equals(warp.dim()) && Double.isFinite(warp.x()) && Double.isFinite(warp.y())
                    && Double.isFinite(warp.z()) && Float.isFinite(warp.headYaw()) && Float.isFinite(warp.pitch());
        }

        private static Settlement point(com.fibermc.essentialcommands.types.WarpLocation warp) {
            return new Settlement(warp.getName(), warp.x(), warp.y(), warp.z(), warp.headYaw(), warp.pitch());
        }

        static boolean exists(String name) { return manager().getWarp(name) != null; }

        static void create(ServerPlayer player, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            manager().setWarp(name, new com.fibermc.essentialcommands.types.MinecraftLocation(player), false);
        }

        static boolean delete(String name) {
            return manager().delWarp(name);
        }
    }
}
