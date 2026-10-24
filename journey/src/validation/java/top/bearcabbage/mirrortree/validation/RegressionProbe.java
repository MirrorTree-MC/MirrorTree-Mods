package top.bearcabbage.mirrortree.validation;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import top.bearcabbage.mirrortree.*;
import top.bearcabbage.mirrortree.dream.MTDream;
import top.bearcabbage.mirrortree.mixin.FoodDataAccessor;
import top.bearcabbage.lanterninstorm.content.*;
import top.bearcabbage.lanterninstorm.safety.*;
import top.bearcabbage.lanterninstorm.progression.PlayerStore;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;

public final class RegressionProbe implements ModInitializer {
    private int ticks, phase;
    private ServerPlayer player;
    private final List<String> passed = new ArrayList<>();
    private Object spawn;
    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed.add(message);
        System.out.println("BEDROOM_FIX_CHECK: " + message);
    }
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                if (++ticks == 40) { run(server); phase = 1; }
                else if (phase == 1 && player.level().dimension().equals(Level.OVERWORLD)) {
                    check(player.gameMode() == GameType.SURVIVAL, "first dream restores survival");
                    var s = PlayerStore.get(player);
                    check(s.spawn != null && s.spawnDimension.equals("minecraft:overworld"), "first dream registers LIStorm origin");
                    check(spawn.equals(server.getWorldData().overworldData().getRespawnData()), "global bedroom spawn is unchanged");
                    finish(server, null);
                } else if (ticks > 2400 && phase != 2) throw new AssertionError("dream search timed out");
            } catch (Throwable error) { finish(server, error); }
        });
    }
    private void run(MinecraftServer server) throws Exception {
        spawn = server.getWorldData().overworldData().getRespawnData();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "dream-probe"), false);
        player = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        var bedroom = server.getLevel(MirrorTree.bedroom);
        ServerLevel stars = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse("starry_skies:overworld")));
        check(bedroom != null && stars != null, "production bedroom and Starry Skies dimensions loaded");
        player.teleportTo(bedroom, 100.5, 200, 100.5, Set.of(), 0, 0, false);
        player.setGameMode(GameType.SURVIVAL);
        var state = PlayerStore.get(player);
        state.graceTicks = 0; state.spawn = null; state.safe = true; state.cleared = true;
        player.addEffect(new MobEffectInstance(AnnoyingEffects.TANGLING_DREAMS, 200));
        player.addEffect(new MobEffectInstance(AnnoyingEffects.TANGLING_NIGHTMARE, 200));
        tickSafety();
        check(state.safe && !player.hasEffect(AnnoyingEffects.TANGLING_DREAMS)
                && !player.hasEffect(AnnoyingEffects.TANGLING_NIGHTMARE), "bedroom cleans persisted nightmares even when already marked safe");
        for (var item : List.of(StormContent.FLASHLIGHT, StormContent.TALISMAN)) {
            var stack = new ItemStack(item); stack.setDamageValue(50);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            for(int i=0;i<5;i++) tickSafety();
            check(stack.getDamageValue()==50, "bedroom preserves durability: " + item);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        for (var level : List.of(bedroom, stars)) {
            player.teleportTo(level, 10.5, 200, 12.5, Set.of(), 0, 0, false);
            player.setGameMode(GameType.CREATIVE);
            BlockPos target = new BlockPos(10,200,10);
            level.setBlockAndUpdate(target.below(), Blocks.STONE.defaultBlockState());
            for (var entry : StormContent.LAMPS.entrySet()) {
                var lamp=entry.getValue();
                level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
                var stack=new ItemStack(lamp.asItem());
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,
                        new BlockHitResult(Vec3.atCenterOf(target),Direction.UP,target,false));
                if (stack.getItem() instanceof BlockItem item)
                    check(item.place(context).consumesAction() && level.getBlockState(target).is(lamp),
                            level.dimension().identifier()+" placed "+entry.getKey());
                else check(lamp.getStateForPlacement(context)!=null, "unregistered white-paper lamp block can be placed");
            }
            level.setBlockAndUpdate(target,Blocks.AIR.defaultBlockState());
        }
        BlockPos target=new BlockPos(10,200,10);
        var hit=new BlockHitResult(Vec3.atCenterOf(target),Direction.UP,target,false);
        var ordinary=StormContent.LAMPS.get("oak_wooden_lantern");
        stars.setBlockAndUpdate(target,ordinary.defaultBlockState());
        stars.getBlockState(target).useWithoutItem(stars,player,hit);
        check(!stars.getBlockState(target).getValue(SpiritLanternBlock.STARTUP), "ordinary Starry Skies lamp still cannot activate protection");
        var metal=StormContent.LAMPS.get("midas_gold_lantern");
        stars.setBlockAndUpdate(target,metal.defaultBlockState());
        stars.getBlockState(target).useWithoutItem(stars,player,hit);
        check(stars.getBlockState(target).getValue(SpiritLanternBlock.STARTUP)
                && LanternIndex.isSafe(stars,Vec3.atCenterOf(target)), "metal lamp protection retained");
        stars.setBlockAndUpdate(target,Blocks.AIR.defaultBlockState());
        player.setGameMode(GameType.SURVIVAL);
        var doll=new ItemStack(StormContent.FLASHLIGHT);
        doll.setDamageValue(doll.getMaxDamage()-1);
        player.setItemInHand(InteractionHand.MAIN_HAND,doll);
        state.graceTicks=0;state.cleared=false;
        tickSafety();
        var dreamEffect=player.getEffect(AnnoyingEffects.TANGLING_DREAMS);
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,200));
        for(int i=0;i<5;i++){
            tickSafety();
            check(player.hasEffect(MobEffects.SLOWNESS)
                    && player.getEffect(AnnoyingEffects.TANGLING_DREAMS)==dreamEffect,
                    "exhausted doll preserves debuffs, second "+i);
        }
        doll.setDamageValue(50);tickSafety();
        check(!player.hasEffect(MobEffects.SLOWNESS), "recharged doll cleanses again");
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        player.removeAllEffects();
        var overworld=server.overworld();
        BlockPos dreamBed=new BlockPos(20,200,20);
        platform(overworld,dreamBed);
        overworld.setBlockAndUpdate(dreamBed,Blocks.RED_BED.defaultBlockState());
        player.teleportTo(overworld,20.5,200,21.5,Set.of(),35,0,false);
        check(!MTDream.wakeup(player), "wakeup is rejected when not sleeping");
        player.setHealth(12);player.getFoodData().setFoodLevel(7);player.getFoodData().setSaturation(2);
        ((FoodDataAccessor)player.getFoodData()).mirrortree$setExhaustion(1.25f);
        player.addEffect(new MobEffectInstance(MobEffects.SPEED,600));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,600));
        player.setAbsorptionAmount(1);
        platform(bedroom,new BlockPos(4239,66,799));
        player.startSleeping(dreamBed);
        check(MTDream.wakeup(player) && player.level()==bedroom && player.gameMode()==GameType.ADVENTURE,
                "sleeping overworld player wakes in bedroom adventure mode");
        check(!player.hasEffect(MobEffects.SPEED),"dream effects suspended in bedroom");
        player.setHealth(20);player.getFoodData().setFoodLevel(20);
        var bed=new BlockPos(4241,66,799);
        bedroom.setBlockAndUpdate(bed,Blocks.RED_BED.defaultBlockState());
        var bedHit=new BlockHitResult(Vec3.atCenterOf(bed),Direction.UP,bed,false);
        var result=UseBlockCallback.EVENT.invoker().interact(player,bedroom,InteractionHand.MAIN_HAND,bedHit);
        check(result.consumesAction() && player.level()==overworld && player.gameMode()==GameType.SURVIVAL,
                "bed callback restores prior dream and survival mode");
        check(player.hasEffect(MobEffects.SPEED) && player.getEffect(MobEffects.SPEED).getDuration()==600,
                "effect snapshot survives removal and disk reload");
        check(player.getAbsorptionAmount()==1, "remaining absorption survives wakeup and disk reload");
        check(player.getHealth()==12 && player.getFoodData().getFoodLevel()==7
                && player.getFoodData().getSaturationLevel()==2
                && ((FoodDataAccessor)player.getFoodData()).mirrortree$getExhaustion()==1.25f,
                "health, hunger, saturation and exhaustion restored");
        check(player.getX()>=16 && player.getX()<=24, "return uses saved position rather than random spawn");
        player.teleportTo(bedroom,4239.5,66,799.5,Set.of(),0,0,false);
        player.setGameMode(GameType.ADVENTURE);
        var blockHit=new BlockHitResult(Vec3.atCenterOf(bed.below()),Direction.UP,bed.below(),false);
        check(UseBlockCallback.EVENT.invoker().interact(player,bedroom,InteractionHand.MAIN_HAND,blockHit)==InteractionResult.FAIL,
                "ordinary bedroom building permission remains restricted");
        checkBedroomDrops(bedroom);
        // Prior return marked the disk snapshot inactive: this starts a fresh bounded search.
        bedroom.setBlockAndUpdate(bed,Blocks.RED_BED.defaultBlockState());
        bedroom.setBlockAndUpdate(bed.north(),Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        SearchRegressionChecks.run(server, player, bed, this::check);
        var firstResult=UseBlockCallback.EVENT.invoker().interact(player,bedroom,InteractionHand.MAIN_HAND,bedHit);
        check(firstResult.consumesAction(), "first-dream bed callback consumed interaction");
        var field=MTDream.class.getDeclaredField("SEARCHES");field.setAccessible(true);
        check(((Map<?,?>)field.get(null)).containsKey(player.getUUID()) || player.level()==overworld,
                "first-dream search queued or completed immediately");
    }
    private void checkBedroomDrops(ServerLevel bedroom) {
        var nearby = AABB.ofSize(player.position(), 32, 32, 32);
        int before = bedroom.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, nearby).size();
        player.inventoryMenu.setCarried(new ItemStack(Items.DIAMOND, 7));
        for (int button : new int[] {0, 1}) {
            player.inventoryMenu.clicked(-999, button, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
            check(player.inventoryMenu.getCarried().getCount()==7, "outside click preserves carried stack: " + button);
        }
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
        player.getInventory().setSelectedSlot(0);
        for (int button : new int[] {0, 1}) {
            player.inventoryMenu.clicked(36, button, net.minecraft.world.inventory.ContainerInput.THROW, player);
            check(player.getInventory().countItem(Items.DIAMOND)==5, "inventory throw preserves stack: " + button);
        }
        player.drop(false); player.drop(true);
        check(player.getInventory().countItem(Items.DIAMOND)==5, "server hotkey drop preserves selected stack");
        check(bedroom.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, nearby).size()==before,
                "blocked drops do not create duplicate items");
        player.inventoryMenu.setCarried(new ItemStack(Items.EMERALD, 2));
        player.inventoryMenu.clicked(9, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        check(player.inventoryMenu.getCarried().isEmpty() && player.getInventory().countItem(Items.EMERALD)==2,
                "ordinary inventory movement remains available");
        player.getInventory().clearContent();
        for (int slot=0;slot<36;slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        player.inventoryMenu.setCarried(new ItemStack(Items.DIAMOND, 7));
        player.inventoryMenu.removed(player);
        check(player.inventoryMenu.getCarried().isEmpty()
                && bedroom.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, nearby).stream()
                        .filter(item -> item.getItem().is(Items.DIAMOND)).mapToInt(item -> item.getItem().getCount()).sum()==7,
                "closing a full inventory preserves overflow items");
        player.getInventory().clearContent();
        player.setGameMode(GameType.CREATIVE);
        player.inventoryMenu.setCarried(new ItemStack(Items.EMERALD, 3));
        player.inventoryMenu.clicked(-999, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        check(player.inventoryMenu.getCarried().isEmpty(), "creative administrators can still drop items");
        player.setGameMode(GameType.ADVENTURE);
    }
    private void tickSafety(){ PlayerStore.get(player).tick=19;SafetyService.tick(player); }
    private static void platform(ServerLevel level,BlockPos pos){
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){
            level.setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState());
            for(int y=0;y<3;y++)level.setBlockAndUpdate(pos.offset(x,y,z),Blocks.AIR.defaultBlockState());
        }
    }
    private void finish(MinecraftServer server,Throwable error){
        if(phase==2)return;
        phase=2;
        if(error!=null)error.printStackTrace();
        try {
            var result=new com.google.gson.JsonObject();
            result.addProperty("passed",error==null);
            result.add("checks",new com.google.gson.Gson().toJsonTree(passed));
            if(error!=null)result.addProperty("error",error.toString());
            Files.writeString(Path.of("bedroom-fix-regression.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
        }catch(Exception e){e.printStackTrace();}
        if(player!=null)server.getPlayerList().remove(player);
        server.halt(false);
    }
}
