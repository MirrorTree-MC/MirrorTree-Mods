package top.bearcabbage.annoyingeffects;

import static net.minecraft.world.effect.MobEffects.*;

import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.ai.attributes.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.*;
import top.bearcabbage.annoyingeffects.effect.*;
import top.bearcabbage.annoyingeffects.utils.ConfigReadAndWrite;

public class AnnoyingEffects implements ModInitializer {
  public static final String MOD_ID = "annoyingeffects";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
  private static final ConfigReadAndWrite config =
      new ConfigReadAndWrite(
          FabricLoader.getInstance().getConfigDir().resolve("AnnoyingEffects.json"));
  public static final Holder<MobEffect> ANOREXIA =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "anorexia"),
          new AnorexiaStatusEffect());
  public static final Holder<MobEffect> CARROT_CURSE =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "carrot_curse"),
          new CarrotCurseStatusEffect());
  public static final Holder<MobEffect> CHANNELING =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "channeling"),
          new ChannelingStatusEffect());
  public static final Holder<MobEffect> CHAOTIC_EXPLOSION =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "chaotic_explosion"),
          new ChaoticExplosionStatusEffect());
  public static final Holder<MobEffect> CHAOTIC_TELEPORTATION =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "chaotic_teleportation"),
          new ChaoticTeleportationStatusEffect());
  public static final Holder<MobEffect> CONTROLS_ALWAYS_DIG =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "controls_always_dig"),
          new ControlsAlwaysDigStatusEffect());
  public static final Holder<MobEffect> CONTROLS_ALWAYS_JUMP =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "controls_always_jump"),
          new ControlsAlwaysJumpStatusEffect());
  public static final Holder<MobEffect> CONTROLS_CHAOTIC_USE =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "controls_chaotic_use"),
          new ControlsChaoticUseStatusEffect());
  public static final Holder<MobEffect> CONTROLS_CRAB =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "controls_crab"),
          new ControlsCrabStatusEffect());
  public static final Holder<MobEffect> CONTROLS_MIRROR =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "controls_mirror"),
          new ControlsMirrorStatusEffect());
  public static final Holder<MobEffect> CONTROLS_SHORT_REACH =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "controls_short_reach"),
          new ControlsShortReachStatusEffect()
              .addAttributeModifier(
                  Attributes.BLOCK_INTERACTION_RANGE,
                  Identifier.parse("annoyingeffect.controls_short_range"),
                  -1.0F,
                  AttributeModifier.Operation.ADD_VALUE)
              .addAttributeModifier(
                  Attributes.ENTITY_INTERACTION_RANGE,
                  Identifier.parse("annoyingeffect.controls_short_range"),
                  -1.0F,
                  AttributeModifier.Operation.ADD_VALUE));
  public static final Holder<MobEffect> CRAWLER =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "crawler"),
          new CrawlerStatusEffect());
  public static final Holder<MobEffect> CREEPERPHOBIA =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "creeperphobia"),
          new CreeperphobiaStatusEffect());
  public static final Holder<MobEffect> CURSE_OF_VANISHING =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "curse_of_vanishing"),
          new CurseOfVanishingStatusEffect());
  public static final Holder<MobEffect> DISABLE_CRAFTING_TABLE =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "disable_crafting_table"),
          new DisableCraftingTableStatusEffect());
  public static final Holder<MobEffect> DISABLE_INVENTORY =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "disable_inventory"),
          new DisableInventoryStatusEffect());
  public static final Holder<MobEffect> DISABLE_SLEEPING =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "disable_sleeping"),
          new DisableSleepingStatusEffect());
  public static final Holder<MobEffect> ENDERMAN_HOSTILE =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "enderman_hostile"),
          new EndermanHostileStatusEffect());
  public static final Holder<MobEffect> HEAVINESS =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "heaviness"),
          new HeavinessStatusEffect()
              .addAttributeModifier(
                  Attributes.SAFE_FALL_DISTANCE,
                  Identifier.parse("annoyingeffect.heaviness.jump"),
                  -2.25F,
                  AttributeModifier.Operation.ADD_VALUE)
              .addAttributeModifier(
                  Attributes.FALL_DAMAGE_MULTIPLIER,
                  Identifier.parse("annoyingeffect.heaviness.jump"),
                  1.0F,
                  AttributeModifier.Operation.ADD_VALUE)
              .addAttributeModifier(
                  Attributes.GRAVITY,
                  Identifier.parse("annoyingeffect.heaviness.gravity"),
                  1.0F,
                  AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
  public static final Holder<MobEffect> HORSELESS =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "horseless"),
          new HorselessStatusEffect());
  public static final Holder<MobEffect> MISFORTUNE =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "misfortune"),
          new MisfortuneStatusEffect());
  public static final Holder<MobEffect> OPPRESSED =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "oppressed"),
          new OppressedStatusEffect());
  public static final Holder<MobEffect> REALLY_COLD =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "really_cold"),
          new ReallyColdStatusEffect());
  public static final Holder<MobEffect> REALLY_HOT =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "really_hot"),
          new ReallyHotStatusEffect());
  public static final Holder<MobEffect> REPEATER =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "repeater"),
          new RepeaterStatusEffect());
  public static final Holder<MobEffect> SCHIZOPHRENIA =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "schizophrenia"),
          new SchizophreniaStatusEffect());
  public static final Holder<MobEffect> SLIPPY =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "slippy"),
          new SlippyStatusEffect());
  public static final Holder<MobEffect> SPIN =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "spin"),
          new SpinStatusEffect());
  public static final Holder<MobEffect> SWAPPING_HANDS =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "swapping_hands"),
          new SwappingHandsStatusEffect());
  public static final Holder<MobEffect> TANGLING_NIGHTMARE =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "tangling_nightmare"),
          new TanglingNightmareStatusEffect());
  public static final Holder<MobEffect> TANGLING_DREAMS =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "tangling_dreams"),
          new TanglingDreamsStatusEffect());
  public static final Holder<MobEffect> TARGETED =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "targeted"),
          new TargetedStatusEffect());
  public static final Holder<MobEffect> VOICELESS =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "voiceless"),
          new VoicelessStatusEffect());
  public static final Holder<MobEffect> VULNERABLE =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "vulnerable"),
          new VulnerableStatusEffect());
  public static final Holder<MobEffect> WATER_FILLING =
      Registry.registerForHolder(
          BuiltInRegistries.MOB_EFFECT,
          Identifier.fromNamespaceAndPath(MOD_ID, "water_filling"),
          new WaterFillingStatusEffect());

  public static final Map<Holder<MobEffect>, Map<String, Integer>> STATUS_EFFECT_MAP =
      new LinkedHashMap<>();

  public void onInitialize() {
    loadStatusEffects();
    config.save();
  }

  private void loadStatusEffects() {
    STATUS_EFFECT_MAP.put(ANOREXIA, config.getStatusParameters("anorexia", paraMap(200, 200)));
    STATUS_EFFECT_MAP.put(
        CARROT_CURSE, config.getStatusParameters("carrot_curse", paraMap(60, 1200)));
    STATUS_EFFECT_MAP.put(CHANNELING, config.getStatusParameters("channeling", paraMap(20, 100)));
    STATUS_EFFECT_MAP.put(
        CHAOTIC_EXPLOSION, config.getStatusParameters("chaotic_explosion", paraMap(15, 600, 1)));
    STATUS_EFFECT_MAP.put(
        CHAOTIC_TELEPORTATION,
        config.getStatusParameters("chaotic_teleportation", paraMap(5, 200)));
    STATUS_EFFECT_MAP.put(
        CONTROLS_ALWAYS_DIG,
        config.getStatusParameters("controls_always_dig", paraMap(30, 300, null, 5, 600, null)));
    STATUS_EFFECT_MAP.put(
        CONTROLS_ALWAYS_JUMP,
        config.getStatusParameters("controls_always_jump", paraMap(30, 300, null, 15, 600, null)));
    STATUS_EFFECT_MAP.put(
        CONTROLS_CHAOTIC_USE,
        config.getStatusParameters("controls_chaotic_use", paraMap(120, 600, 1, 30, null, 0)));
    STATUS_EFFECT_MAP.put(
        CONTROLS_CRAB, config.getStatusParameters("controls_crab", paraMap(120, 600)));
    STATUS_EFFECT_MAP.put(
        CONTROLS_MIRROR,
        config.getStatusParameters("controls_mirror", paraMap(120, 600, null, 30, null, null)));
    STATUS_EFFECT_MAP.put(
        CONTROLS_SHORT_REACH,
        config.getStatusParameters("controls_short_reach", paraMap(120, 600, 1, 30, null, 0)));
    STATUS_EFFECT_MAP.put(CRAWLER, config.getStatusParameters("crawler", paraMap(60, 600)));
    STATUS_EFFECT_MAP.put(
        CREEPERPHOBIA,
        config.getStatusParameters("creeperphobia", paraMap(120, 600, null, 60, null, null)));
    STATUS_EFFECT_MAP.put(
        CURSE_OF_VANISHING,
        config.getStatusParameters("curse_of_vanishing", paraMap(50, 100, null, 25, null, null)));
    STATUS_EFFECT_MAP.put(
        DISABLE_CRAFTING_TABLE,
        config.getStatusParameters("disable_crafting_table", paraMap(60, 600, null, 10, 600, 0)));
    STATUS_EFFECT_MAP.put(
        DISABLE_INVENTORY,
        config.getStatusParameters("disable_inventory", paraMap(10, 100, null, 10, null, null)));
    STATUS_EFFECT_MAP.put(
        DISABLE_SLEEPING, config.getStatusParameters("disable_sleeping", paraMap(120, 600)));
    STATUS_EFFECT_MAP.put(
        ENDERMAN_HOSTILE, config.getStatusParameters("enderman_hostile", paraMap(120, 600)));
    STATUS_EFFECT_MAP.put(HEAVINESS, config.getStatusParameters("heaviness", paraMap(60, 600)));
    STATUS_EFFECT_MAP.put(HORSELESS, config.getStatusParameters("horseless", paraMap(5, 300)));
    STATUS_EFFECT_MAP.put(MISFORTUNE, config.getStatusParameters("misfortune", paraMap(-1, -1)));
    STATUS_EFFECT_MAP.put(
        OPPRESSED, config.getStatusParameters("oppressed", paraMap(10, 600, 2, 10, null, 0)));
    STATUS_EFFECT_MAP.put(REALLY_COLD, config.getStatusParameters("really_cold", paraMap(3, 120)));
    STATUS_EFFECT_MAP.put(REALLY_HOT, config.getStatusParameters("really_hot", paraMap(3, 120)));
    STATUS_EFFECT_MAP.put(
        REPEATER, config.getStatusParameters("repeater", paraMap(120, 600, null, 60, null, null)));
    STATUS_EFFECT_MAP.put(
        SCHIZOPHRENIA, config.getStatusParameters("schizophrenia", paraMap(-1, -1)));
    STATUS_EFFECT_MAP.put(SLIPPY, config.getStatusParameters("slippy", paraMap(120, 600)));
    STATUS_EFFECT_MAP.put(SPIN, config.getStatusParameters("spin", paraMap(5, 300, 4, 3, 600, 2)));
    STATUS_EFFECT_MAP.put(
        SWAPPING_HANDS, config.getStatusParameters("swapping_hands", paraMap(10, 200, null)));
    STATUS_EFFECT_MAP.put(TARGETED, config.getStatusParameters("targeted", paraMap(120, 600)));
    STATUS_EFFECT_MAP.put(VOICELESS, config.getStatusParameters("voiceless", paraMap(120, 600)));
    STATUS_EFFECT_MAP.put(
        VULNERABLE,
        config.getStatusParameters("vulnerable", paraMap(120, 600, null, 30, null, null)));
    STATUS_EFFECT_MAP.put(
        WATER_FILLING,
        config.getStatusParameters("water_filling", paraMap(0, 15, null, -1, null, null)));
    STATUS_EFFECT_MAP.put(BLINDNESS, config.getStatusParameters("blindness", paraMap(30, 600)));
    STATUS_EFFECT_MAP.put(
        DARKNESS, config.getStatusParameters("darkness", paraMap(30, 300, null, 15, 600, null)));
    STATUS_EFFECT_MAP.put(
        HUNGER, config.getStatusParameters("hunger", paraMap(60, 600, null, 30, 1200, null)));
    STATUS_EFFECT_MAP.put(INFESTED, config.getStatusParameters("infested", paraMap(120, 600)));
    STATUS_EFFECT_MAP.put(LEVITATION, config.getStatusParameters("levitation", paraMap(10, 200)));
    STATUS_EFFECT_MAP.put(
        MINING_FATIGUE,
        config.getStatusParameters("mining_fatigue", paraMap(60, 600, null, 30, null, null)));
    STATUS_EFFECT_MAP.put(NAUSEA, config.getStatusParameters("nausea", paraMap(10, 200)));
    STATUS_EFFECT_MAP.put(
        SLOWNESS, config.getStatusParameters("slowness", paraMap(60, 600, 1, 30, null, 0)));
    STATUS_EFFECT_MAP.put(
        WEAKNESS, config.getStatusParameters("weakness", paraMap(120, 600, 1, 30, null, 0)));
    STATUS_EFFECT_MAP.put(WITHER, config.getStatusParameters("wither", paraMap(10, 600)));
  }

  private Map<String, Integer> paraMap(Integer duration, Integer interval) {
    return paraMap(duration, interval, null);
  }

  private Map<String, Integer> paraMap(
      Integer duration, Integer interval, @Nullable Integer amplifier) {
    return paraMap(duration, interval, amplifier, null, null, null);
  }

  private Map<String, Integer> paraMap(
      Integer duration,
      Integer interval,
      @Nullable Integer amplifier,
      @Nullable Integer weak_duration,
      @Nullable Integer weak_interval,
      @Nullable Integer weak_amplifier) {
    if (amplifier == null) amplifier = 0;
    if (weak_duration == null) weak_duration = -1;
    if (weak_interval == null) weak_interval = interval;
    if (weak_amplifier == null) weak_amplifier = amplifier;
    return Map.of(
        "duration", duration,
        "interval", interval,
        "amplifier", amplifier,
        "weak_duration", weak_duration,
        "weak_interval", weak_interval,
        "weak_amplifier", weak_amplifier);
  }
}
