package top.bearcabbage.mirrortree.validation;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import top.bearcabbage.lanterninstorm.content.BeginningLanternEntity;
import top.bearcabbage.lanterninstorm.progression.PlayerStore;
import top.bearcabbage.mirrortree.MirrorTree;
import top.bearcabbage.mirrortree.dream.MTDream;
import top.bearcabbage.mirrortree.settlement.DreamSelectionMenu;
import top.bearcabbage.mirrortree.settlement.SettlementWarps;

/** Real menu, Brigadier permissions and Essential Commands checks, only in disposable worlds. */
final class SettlementRegressionChecks {
    static void run(MinecraftServer server, ServerPlayer player, BlockPos bed,
            BiConsumer<Boolean, String> check) throws Exception {
        var bedroom = player.level();
        var position = player.position();
        MTDream.enter(player, bed);
        check.accept(player.containerMenu instanceof DreamSelectionMenu, "bed opens server-owned dream selection menu");
        var menu = player.containerMenu;
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
        menu.setCarried(new ItemStack(Items.EMERALD, 3));
        for (ContainerInput input : ContainerInput.values()) {
            if (input == ContainerInput.PICKUP) continue;
            menu.clicked(DreamSelectionMenu.RANDOM_SLOT, 0, input, player);
            menu.clicked(27, 0, input, player);
        }
        menu.clicked(-999, 0, ContainerInput.PICKUP, player);
        menu.clicked(27, 0, ContainerInput.PICKUP, player);
        check.accept(player.level() == bedroom && player.getInventory().countItem(Items.DIAMOND) == 7
                && menu.getCarried().getCount() == 3 && menu.getCarried().is(Items.EMERALD),
                "menu rejects shift, swap, clone, drag, throw, outside and inventory clicks without losing or duplicating items");
        player.closeContainer();
        check.accept(player.getInventory().countItem(Items.EMERALD)==3,
                "closing dream menu returns the player's real cursor item");
        player.getInventory().clearContent();
        if (SettlementWarps.available()) EssentialChecks.run(server, player, bed, check);
        else check.accept(SettlementWarps.accessible(player).isEmpty(), "missing optional Essential Commands keeps random entry available");
        player.teleportTo(bedroom, position.x, position.y, position.z, Set.of(), 0, 0, false);
        player.setGameMode(GameType.ADVENTURE);
        player.closeContainer();
    }

    private static final class EssentialChecks {
        static void run(MinecraftServer server, ServerPlayer player, BlockPos bed,
                BiConsumer<Boolean, String> check) throws Exception {
            var bedroom = server.getLevel(MirrorTree.bedroom);
            var position = player.position();
            var originalSpawn = server.getWorldData().overworldData().getRespawnData();
            var manager = com.fibermc.essentialcommands.ManagerLocator.getInstance().getWorldDataManager();
            var permissionEvent=Class.forName("me.lucko.fabric.api.permissions.v0.PermissionCheckEvent");
            @SuppressWarnings("unchecked")
            var event=(net.fabricmc.fabric.api.event.Event<Object>)permissionEvent.getField("EVENT").get(null);
            event.register(java.lang.reflect.Proxy.newProxyInstance(permissionEvent.getClassLoader(),new Class<?>[]{permissionEvent},
                    (proxy,method,args)-> {
                        if (method.getDeclaringClass()==Object.class) return switch(method.getName()) {
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy==args[0];
                            default -> "isolated settlement permission backend";
                        };
                        return args[1].toString().endsWith("聚落：probe-private")
                                ? net.fabricmc.fabric.api.util.TriState.FALSE : net.fabricmc.fabric.api.util.TriState.DEFAULT;
                    }));
            var commands = server.getCommands().getDispatcher();
            var point = new BlockPos(32, 200, 32);
            for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++) {
                server.overworld().setBlockAndUpdate(point.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                for (int y=0;y<4;y++) server.overworld().setBlockAndUpdate(point.offset(x,y,z),Blocks.AIR.defaultBlockState());
            }
            player.teleportTo(server.overworld(),32.5,200,32.5,Set.of(),67,12,false);
            // Embedded players do not send chunk tracking acknowledgements during this test tick.
            // Promote only this disposable fixture's entity section before checking marker events.
            var entityField=net.minecraft.server.level.ServerLevel.class.getDeclaredField("entityManager");entityField.setAccessible(true);
            var entities=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)entityField.get(server.overworld());
            entities.updateChunkStatus(new net.minecraft.world.level.ChunkPos(point.getX() >> 4, point.getZ() >> 4),
                    net.minecraft.world.level.entity.Visibility.TRACKED);
            var admin = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER);
            boolean denied = false;
            try { commands.execute("mtwarp denied-probe",player.createCommandSourceStack()); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
            check.accept(denied && !SettlementWarps.exists("聚落：denied-probe"), "ordinary player cannot create settlements");
            check.accept(commands.execute("mtwarp probe00",admin)==1 && SettlementWarps.exists("聚落：probe00"),
                    "mtwarp persists a prefixed Essential Commands warp");
            var warp = SettlementWarps.resolve(player,"聚落：probe00");
            check.accept(warp != null && warp.blockPos().equals(point) && warp.yaw()==67 && warp.pitch()==12,
                    "settlement retains primary dimension, exact position and facing");
            check.accept(server.overworld().getEntities(BeginningLanternEntity.WARP,
                    e->e.hasCustomName() && e.getCustomName().getString().equals("聚落入梦点[probe00]")).size()==1,
                    "mtwarp creates exactly one persistent settlement lantern");
            boolean duplicate = false;
            try { commands.execute("mtwarp probe00",admin); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { duplicate = true; }
            check.accept(duplicate, "duplicate settlement cannot overwrite warp or duplicate its marker");
            commands.execute("mtwarp 桃花源",admin);
            commands.execute("mtwarp \"林间 小屋\"",admin);
            check.accept(SettlementWarps.exists("聚落：桃花源") && SettlementWarps.exists("聚落：林间 小屋"),
                    "settlement commands support unquoted Chinese and quoted names with spaces");
            commands.execute("mtwarp-delete 桃花源",admin);
            commands.execute("mtwarp-delete \"林间 小屋\"",admin);
            check.accept(!SettlementWarps.exists("聚落：桃花源") && !SettlementWarps.exists("聚落：林间 小屋"),
                    "delete accepts the same Chinese and quoted name syntax");
            var location = new com.fibermc.essentialcommands.types.MinecraftLocation(player);
            manager.setWarp("probe-ordinary",location,false);
            manager.setWarp("聚落：probe-private",location,true);
            manager.setWarp("聚落：probe-bedroom",new com.fibermc.essentialcommands.types.MinecraftLocation(
                    MirrorTree.bedroom,0,100,0),false);
            var names = SettlementWarps.accessible(player).stream().map(SettlementWarps.Settlement::name).toList();
            check.accept(names.contains("聚落：probe00") && !names.contains("probe-ordinary")
                    && !names.contains("聚落：probe-private") && !names.contains("聚落：probe-bedroom"),
                    "menu filters exact Chinese prefix, primary dimension and Essential Commands permissions");
            for (int i=1;i<=20;i++) commands.execute(String.format(java.util.Locale.ROOT,"mtwarp probe%02d",i),admin);
            player.teleportTo(bedroom,position.x,position.y,position.z,Set.of(),0,0,false);
            boolean wrongDimension=false;
            try { commands.execute("mtwarp bedroom-denied",player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER)); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { wrongDimension=true; }
            check.accept(wrongDimension && !SettlementWarps.exists("聚落：bedroom-denied"), "mtwarp cannot silently register bedroom coordinates as overworld");
            check.accept(!MTDream.select(player,bed,"聚落：probe-private") && player.level()==bedroom,
                    "direct selection cannot bypass Essential Commands warp permissions");
            manager.setWarp("聚落：probe-height",new com.fibermc.essentialcommands.types.MinecraftLocation(
                    Level.OVERWORLD,32,server.overworld().getMaxY()+100,32),false);
            check.accept(!MTDream.select(player,bed,"聚落：probe-height"), "invalid height is rejected before loading destination chunks");
            manager.delWarp("聚落：probe-height");
            MTDream.enter(player,bed);
            player.containerMenu.clicked(DreamSelectionMenu.SETTLEMENTS_SLOT,0,ContainerInput.PICKUP,player);
            var firstPage=player.containerMenu;
            check.accept(firstPage instanceof DreamSelectionMenu && firstPage.getSlot(26).hasItem(), "more than eighteen settlements shows next page");
            firstPage.clicked(26,0,ContainerInput.PICKUP,player);
            check.accept(player.containerMenu != firstPage && player.containerMenu.getSlot(18).hasItem()
                    && player.containerMenu.getSlot(0).getItem().getHoverName().getString().equals("probe18"),
                    "settlement pagination reaches remaining entries");
            player.closeContainer();
            resetBudget(server);
            check.accept(MTDream.select(player,bed,"聚落：probe00") && player.level()==server.overworld(),
                    "settlement selection travels to a loaded safe warp");
            check.accept(PlayerStore.get(player).spawnDimension.equals("minecraft:overworld")
                    && java.util.Arrays.equals(PlayerStore.get(player).spawn,new int[]{32,200,32})
                    && player.gameMode()==GameType.SURVIVAL && player.getRespawnConfig().respawnData().dimension().equals(Level.OVERWORLD),
                    "joining settlement registers survival mode, LIStorm origin and personal respawn");
            check.accept(originalSpawn.equals(server.getWorldData().overworldData().getRespawnData()),
                    "settlement join does not alter global bedroom spawn");
            player.teleportTo(bedroom,position.x,position.y,position.z,Set.of(),0,0,false);
            player.setGameMode(GameType.ADVENTURE);
            // Reserve the selection, then delete it before chunk scheduling may run.
            var budget=MTDream.class.getDeclaredField("workThisTick");budget.setAccessible(true);budget.setLong(null,2_000_000);
            var workTick=MTDream.class.getDeclaredField("workTick");workTick.setAccessible(true);workTick.setInt(null,server.getTickCount());
            check.accept(MTDream.select(player,bed,"聚落：probe00"), "settlement selection can wait in shared queue");
            check.accept(commands.execute("mtwarp-delete probe00",admin)==1 && !SettlementWarps.exists("聚落：probe00"),
                    "mtwarp-delete removes saved warp");
            resetBudget(server);
            var tick=MTDream.class.getDeclaredMethod("tick",MinecraftServer.class);tick.setAccessible(true);tick.invoke(null,server);
            var queue=MTDream.class.getDeclaredField("SEARCHES");queue.setAccessible(true);
            check.accept(player.level()==bedroom && !((Map<?,?>)queue.get(null)).containsKey(player.getUUID()),
                    "deleted warp cannot teleport a previously queued player");
            check.accept(server.overworld().getEntities(BeginningLanternEntity.WARP,
                    e->e.hasCustomName() && e.getCustomName().getString().equals("聚落入梦点[probe00]")).isEmpty(),
                    "delete clears loaded settlement markers");
            var orphan=new BeginningLanternEntity(BeginningLanternEntity.WARP,server.overworld());
            orphan.setPos(32.5,200.5,32.5);orphan.addTag("mirrortree_settlement");
            orphan.setCustomName(net.minecraft.network.chat.Component.literal("聚落入梦点[probe00]"));
            server.overworld().addFreshEntity(orphan);
            check.accept(orphan.isRemoved(), "deleted managed marker is discarded when it later loads");
            for (int i=1;i<=20;i++) manager.delWarp(String.format(java.util.Locale.ROOT,"聚落：probe%02d",i));
            for (String name:List.of("probe-ordinary","聚落：probe-private","聚落：probe-bedroom")) manager.delWarp(name);
            manager.save();
        }

        private static void resetBudget(MinecraftServer server) throws Exception {
            var field=MTDream.class.getDeclaredField("workTick");field.setAccessible(true);field.setInt(null,-1);
        }
    }
}
