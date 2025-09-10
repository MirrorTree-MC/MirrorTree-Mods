package top.bearcabbage.mirrortree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.text2speech.Narrator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.bearcabbage.mirrortree.dream.MTDream;
import top.bearcabbage.mirrortree.dream.MTDreamingPoint;
import xyz.nikitacartes.easyauth.utils.PlayerAuth;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
public class MirrorTree implements ModInitializer {
	public static final String MOD_ID = "mirrortree";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final MTConfig config = new MTConfig(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("config.json"));
	public static final MTConfig dream = new MTConfig(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("dream_data.json"));

	public static RegistryKey<World> bedroom = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(MOD_ID, "bedroom"));
	public static int bedroomX;
	public static int bedroomY;
	public static int bedroomZ;
	public static int bedroomX_init;
	public static int bedroomY_init;
	public static int bedroomZ_init;

	public static final Map<ServerPlayerEntity, Integer> fresh_player = new ConcurrentHashMap<>();

	@Override
	public void onInitialize() {

		bedroomX = config.getInt("bedroomX", 4239);
		bedroomY = config.getInt("bedroomY", 66);
		bedroomZ = config.getInt("bedroomZ", 799);
		bedroomX_init = config.getInt("bedroomX_init", 4258);
		bedroomY_init = config.getInt("bedroomY_init", 68);
		bedroomZ_init = config.getInt("bedroomZ_init", 796);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment)->MTCommand.registerCommands(dispatcher)); // 调用静态方法注册命令

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			MTDream.dreamingPos.clear();
            try {
				dream.getAll(Map.class).forEach((uuid, pos) -> {
					ArrayList<Double> posList = (ArrayList<Double>) pos;
					double[] posArray = new double[3];
					for (int i = 0; i < 3; i++) {
						posArray[i] = posList.get(i);
					}
					MTDream.dreamingPos.put(UUID.fromString((String) uuid), posArray);
				});
            } catch (Exception e) {
				LOGGER.error(e.getMessage());
            }
			CompletableFuture.runAsync(() -> MTDreamingPoint.init(server.getOverworld()));
        });

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MTDream.dreamingPos.forEach((uuid, pos) -> dream.set(String.valueOf(uuid), pos));
			dream.save();
		});


		EntitySleepEvents.START_SLEEPING.register((entity, sleepingLocation) -> {
			String wakeTarget = "卧室";
			String wakeMsg = "出生点大厅「卧室」";
			entity.sendMessage(Text.literal("\n"));
			entity.sendMessage(Text.literal("========= [你躺下了] =========").formatted(Formatting.BOLD).withColor(0xDDD605).styled((style) -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("这里是梦境世界旅行导航，点击下面的文字可以到达对应的世界")))));
			entity.sendMessage(Text.literal("\n"));
			entity.sendMessage(Text.literal(" ⏰要试着醒来吗？").formatted(Formatting.BOLD).withColor(0xDDD605));
			entity.sendMessage(Text.literal(" ——————>「"+ wakeTarget +"」").formatted(Formatting.BOLD).formatted(Formatting.BLUE).styled((style) -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wakeup")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点击回到"+ wakeMsg)))));
			entity.sendMessage(Text.literal("\n"));
			entity.sendMessage(Text.literal("===== [棱镜树]梦境世界导航 =====").formatted(Formatting.BOLD).withColor(0xDDD605).styled((style) -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("这里是梦境世界旅行导航，点击下面的文字可以到达对应的世界")))));
			entity.sendMessage(Text.literal("\n"));
		});


		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.getRegistryKey().getValue().equals(Identifier.of(MOD_ID,"bedroom"))) {
				if (!world.isClient && !player.isCreative() && !(world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof BedBlock || world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof DoorBlock || world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof GrassBlock || world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof LecternBlock || world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof StairsBlock)) return ActionResult.FAIL;
				if (world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof BedBlock) {
					if (world.isClient) return ActionResult.SUCCESS;
					((ServerPlayerEntity) player).sendMessage(Text.literal("你躺在床上，闭上了眼睛……"));
					MTDream.queueDreamingTask(player.getServer().getWorld(World.OVERWORLD), (ServerPlayerEntity) player, null);
					return ActionResult.SUCCESS;
				}
			}
            return ActionResult.PASS;
        });

		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.getRegistryKey().getValue().equals(Identifier.of(MOD_ID,"bedroom"))) {
				if (entity instanceof VillagerEntity) return ActionResult.PASS;
				if (!player.isCreative()) return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.getRegistryKey().getValue().equals(Identifier.of(MOD_ID,"bedroom"))) {
				if (!player.isCreative()) return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			if (player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS)) == 0
					&& player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.WALK_ONE_CM)) == 0) {
				player.setYaw(90);
				player.setSpawnPoint(bedroom, new BlockPos(bedroomX_init, bedroomY_init, bedroomZ_init), 90, true, false);
				player.changeGameMode(GameMode.ADVENTURE);
				player.sendMessage(Text.literal("欢迎来到棱镜树·山木周目！\n进入小屋右键床来开始游戏吧～").formatted(Formatting.DARK_RED).formatted(Formatting.BOLD).formatted(Formatting.ITALIC));
				player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("你来到了狐狸的生前住所").formatted(Formatting.BOLD)));
				player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("原来这里就是梦境的入口…").formatted(Formatting.GRAY).formatted(Formatting.ITALIC)));
				fresh_player.put(player, 0);
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayerEntity player = handler.player;
			if (((PlayerAuth) player).easyAuth$isAuthenticated() && !((PlayerAuth) player).easyAuth$canSkipAuth()) {
				((PlayerAuth) player).easyAuth$setAuthenticated(false);
				((PlayerAuth) player).easyAuth$saveLastLocation(true);
			}
		});

		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if (world.getRegistryKey().equals(bedroom)) {
				Iterator<Map.Entry<ServerPlayerEntity, Integer>> iterator = fresh_player.entrySet().iterator();
				while (iterator.hasNext()) {
					Map.Entry<ServerPlayerEntity, Integer> entry = iterator.next();
					ServerPlayerEntity player = entry.getKey();
					int time = entry.getValue();
					if (time < 100) {
						fresh_player.put(player, time + 1);
					} else {
						player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("现在，进到房子里去吧…").formatted(Formatting.BOLD).formatted(Formatting.BLUE)));
						player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("去床上睡一觉").formatted(Formatting.GRAY).formatted(Formatting.ITALIC)));
						iterator.remove(); // 使用 Iterator 进行删除操作
					}
				}
			}
			if (world.getTime() % 20 == 0) {
				for (ServerPlayerEntity player : world.getPlayers()) {
					if (!player.isCreative() && !player.isSpectator() && player.getServerWorld().getRegistryKey().equals(bedroom)) player.changeGameMode(GameMode.ADVENTURE);
				}
			}
		});


		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (world.getRegistryKey().equals(bedroom)) {
				if (!player.isCreative()) return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});

	}


	public static class MTConfig {
		private final Path filePath;
		private JsonObject jsonObject;
		private final Gson gson;

		public MTConfig(Path filePath) {
			this.filePath = filePath;
			this.gson = new GsonBuilder().setPrettyPrinting().create();
			try {
				if (Files.notExists(filePath.getParent())) {
					Files.createDirectories(filePath.getParent());
				}
				if (Files.notExists(filePath)) {
					Files.createFile(filePath);
					try (FileWriter writer = new FileWriter(filePath.toFile())) {
						writer.write("{}");
					}
				}

			} catch (IOException e) {
				Narrator.LOGGER.error(e.toString());
			}
			load();
		}

		public void load() {
			try (FileReader reader = new FileReader(filePath.toFile())) {
				this.jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
			} catch (IOException e) {
				this.jsonObject = new JsonObject();
			}
		}

		public void save() {
			try (FileWriter writer = new FileWriter(filePath.toFile())) {
				gson.toJson(jsonObject, writer);
			} catch (IOException e) {
				Narrator.LOGGER.error(e.toString());
			}
		}

		public void set(String key, Object value) {
			jsonObject.add(key, gson.toJsonTree(value));
		}

		public <T> T get(String key, Class<T> clazz) {
			return gson.fromJson(jsonObject.get(key), clazz);
		}

		public <T> T getOrDefault(String key, T defaultValue) {
			if (jsonObject.has(key)) {
				return gson.fromJson(jsonObject.get(key), (Class<T>) defaultValue.getClass());
			}
			else {
				set(key, defaultValue);
				save();
				return defaultValue;
			}
		}

		public int getInt(String key, int defaultValue) {
			if (jsonObject.has(key)) {
				return jsonObject.get(key).getAsInt();
			}
			else {
				set(key, defaultValue);
				save();
				return defaultValue;
			}
		}

		public <T> T getAll(Class<T> clazz) {
			return gson.fromJson(jsonObject, clazz);
		}

	}

	public static class MTCommand {

		public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
			dispatcher.register(LiteralArgumentBuilder.<ServerCommandSource>literal("wakeup")
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayer();
						if (player==null) return 0;
						if (!player.isSleeping()) return 0;
						RegistryKey<World> worldKey = player.getWorld().getRegistryKey();
						if (worldKey.equals(bedroom)) return 0;
						player.wakeUp();
						// 从「梦境」醒来到「卧室」
						if (worldKey.getValue().getNamespace().equals("minecraft")) {
							double[] pos = {player.getBlockPos().getX(), player.getBlockPos().getY(), player.getBlockPos().getZ()};
							MTDream.dreamingPos.put(player.getUuid(), pos);
							ServerWorld bedroom = player.getServer().getWorld(MirrorTree.bedroom);
							player.teleport(bedroom, bedroomX, bedroomY, bedroomZ, 0, 0);
							player.changeGameMode(GameMode.ADVENTURE);
							MTDream.dreamingEffects.put(player.getUuid(), player.getStatusEffects());
							ArrayList<Float> healthAndHunger = new ArrayList<>();
							healthAndHunger.add(player.getHealth());
							healthAndHunger.add((float) player.getHungerManager().getFoodLevel());
							healthAndHunger.add(player.getHungerManager().getSaturationLevel());
							healthAndHunger.add(player.getHungerManager().getExhaustion());
							MTDream.dreamingHealthAndHunger.put(player.getUuid(), healthAndHunger);
							player.clearStatusEffects();
							player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("「卧室」").formatted(Formatting.BOLD).formatted(Formatting.BLUE)));
							player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("你醒来了").formatted(Formatting.WHITE)));
						}
						return 0;
					})
			);

			// 重新入梦
			dispatcher.register(LiteralArgumentBuilder.<ServerCommandSource>literal("redream")
					.requires(source -> source.hasPermissionLevel(2))
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayer();
						if (player==null) return 0;
						try {
							MTDream.queueRedreamingTask(player.getServer().getOverworld(), player);
						} catch (Exception e) {
							LOGGER.error(e.getMessage());
						}
						return 0;
					})
			);

		}
	}
}