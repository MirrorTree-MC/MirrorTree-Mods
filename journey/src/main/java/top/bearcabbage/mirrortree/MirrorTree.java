package top.bearcabbage.mirrortree;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import top.bearcabbage.mirrortree.starryskies.TopTrapdoorDecorator;

public class MirrorTree implements ModInitializer {
 public static final String MOD_ID="mirrortree";
 public static final String SERVER_SEASON_VERSION="AnotherAutumn";
 public static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger(MOD_ID);
 public static final ResourceKey<Level> bedroom=ResourceKey.create(Registries.DIMENSION,Identifier.fromNamespaceAndPath(MOD_ID,"bedroom"));
 public static final MTConfig config=new MTConfig(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("config.json"));
 public static final MTConfig dream=new MTConfig(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("dream_data.json"));
 public static final Item FOX_TAIL_ITEM=item("fox_tail_item",false);
 public static final Item MEAT_ZONGZI=item("meat_zongzi",true);
 public static final Item EGG_ZONGZI=item("egg_zongzi",true);
 public static final Item BERRY_ZONGZI=item("berry_zongzi",true);
 public static final Item TICKET_1=item("ticket_1",false),TICKET_2=item("ticket_2",false),TICKET_3=item("ticket_3",false),TICKET_4=item("ticket_4",false),TICKET_5=item("ticket_5",false);
 private static Item item(String name,boolean food){
  var id=Identifier.fromNamespaceAndPath(MOD_ID,name);
  var properties=new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id));
  if(food)properties.food(new FoodProperties.Builder().nutrition(10).saturationModifier(0.3f).build());
  return Registry.register(BuiltInRegistries.ITEM,id,new Item(properties));
 }
 public void onInitialize(){
  TopTrapdoorDecorator.init();
  top.bearcabbage.mirrortree.dream.MTDream.init();
  UseBlockCallback.EVENT.register((player,world,hand,hit)->{
   if (!world.dimension().equals(bedroom)) return InteractionResult.PASS;
   var block=world.getBlockState(hit.getBlockPos()).getBlock();
   if(block instanceof net.minecraft.world.level.block.BedBlock){
    if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && hand==net.minecraft.world.InteractionHand.MAIN_HAND)
     top.bearcabbage.mirrortree.dream.MTDream.enter(serverPlayer,hit.getBlockPos());
    return InteractionResult.SUCCESS;
   }
   if(!player.isCreative() && !(block instanceof net.minecraft.world.level.block.DoorBlock
     || block instanceof net.minecraft.world.level.block.GrassBlock
     || block instanceof net.minecraft.world.level.block.LecternBlock
     || block instanceof net.minecraft.world.level.block.StairBlock)) return InteractionResult.FAIL;
   return InteractionResult.PASS;
  });
  UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->world.dimension().equals(bedroom)&&!player.isCreative()&&!(entity instanceof Villager)?InteractionResult.FAIL:InteractionResult.PASS);
  AttackEntityCallback.EVENT.register((player,world,hand,entity,hit)->world.dimension().equals(bedroom)&&!player.isCreative()?InteractionResult.FAIL:InteractionResult.PASS);
  AttackBlockCallback.EVENT.register((player,world,hand,pos,direction)->{
   if(!player.isCreative()&&(world.dimension().equals(bedroom)||(world.dimension().identifier().getNamespace().equals("starry_skies")&&world.getBlockState(pos).is(Blocks.REINFORCED_DEEPSLATE))))return InteractionResult.FAIL;
   return InteractionResult.PASS;
  });
 }
}
