package top.bearcabbage.annoyingeffects;

import static net.minecraft.world.effect.MobEffects.*;

import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.ai.attributes.*;
import org.jetbrains.annotations.Nullable;
import top.bearcabbage.annoyingeffects.effect.*;
import top.bearcabbage.annoyingeffects.utils.ConfigReadAndWrite;

public final class AnnoyingEffects {
  public static final String MOD_ID = "annoyingeffects";
  private static final ConfigReadAndWrite config =
      new ConfigReadAndWrite(
          FabricLoader.getInstance().getConfigDir().resolve("AnnoyingEffects.json"));
  public static final Holder<MobEffect> ANOREXIA =
      register("anorexia", new AnnoyingStatusEffect());
  public static final Holder<MobEffect> CARROT_CURSE =
      register("carrot_curse", new CarrotCurseStatusEffect());
  public static final Holder<MobEffect> CHANNELING =
      register("channeling", new ChannelingStatusEffect());
  public static final Holder<MobEffect> CHAOTIC_EXPLOSION =
      register("chaotic_explosion", new ChaoticExplosionStatusEffect());
  public static final Holder<MobEffect> CHAOTIC_TELEPORTATION =
      register("chaotic_teleportation", new ChaoticTeleportationStatusEffect());
  public static final Holder<MobEffect> CONTROLS_ALWAYS_DIG =
      register("controls_always_dig", new ControlsAlwaysDigStatusEffect());
  public static final Holder<MobEffect> CONTROLS_ALWAYS_JUMP =
      register("controls_always_jump", new AnnoyingStatusEffect());
  public static final Holder<MobEffect> CONTROLS_CHAOTIC_USE =
      register("controls_chaotic_use", new AnnoyingStatusEffect());
  public static final Holder<MobEffect> CONTROLS_CRAB =
      register("controls_crab", new AnnoyingStatusEffect());
  public static final Holder<MobEffect> CONTROLS_MIRROR =
      register("controls_mirror", new AnnoyingStatusEffect());
  public static final Holder<MobEffect> CONTROLS_SHORT_REACH =
      register(
          "controls_short_reach",
          new AnnoyingStatusEffect()
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
      register("crawler", new CrawlerStatusEffect());
  public static final Holder<MobEffect> CREEPERPHOBIA =
      register("creeperphobia", new CreeperphobiaStatusEffect());
  public static final Holder<MobEffect> CURSE_OF_VANISHING =
      register("curse_of_vanishing", new AnnoyingStatusEffect());
  public static final Holder<MobEffect> DISABLE_CRAFTING_TABLE =
      register("disable_crafting_table", new AnnoyingStatusEffect());
  public static final Holder<MobEffect> DISABLE_INVENTORY =
      register("disable_inventory", new AnnoyingStatusEffect());
  public static final Holder<MobEffect> DISABLE_SLEEPING =
      register("disable_sleeping", new DisableSleepingStatusEffect());
  public static final Holder<MobEffect> ENDERMAN_HOSTILE =
      register("enderman_hostile", new EndermanHostileStatusEffect());
  public static final Holder<MobEffect> HEAVINESS =
      register(
          "heaviness",
          new AnnoyingStatusEffect()
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
      register("horseless", new HorselessStatusEffect());
  public static final Holder<MobEffect> MISFORTUNE =
      register("misfortune", new AnnoyingStatusEffect());
  public static final Holder<MobEffect> OPPRESSED =
      register("oppressed", new OppressedStatusEffect());
  public static final Holder<MobEffect> REALLY_COLD =
      register("really_cold", new ReallyColdStatusEffect());
  public static final Holder<MobEffect> REALLY_HOT =
      register("really_hot", new ReallyHotStatusEffect());
  public static final Holder<MobEffect> REPEATER =
      register("repeater", new AnnoyingStatusEffect());
  public static final Holder<MobEffect> SCHIZOPHRENIA =
      register("schizophrenia", new SchizophreniaStatusEffect());
  public static final Holder<MobEffect> SLIPPY =
      register("slippy", new AnnoyingStatusEffect());
  public static final Holder<MobEffect> SPIN =
      register("spin", new SpinStatusEffect());
  public static final Holder<MobEffect> SWAPPING_HANDS =
      register("swapping_hands", new SwappingHandsStatusEffect());
  public static final Holder<MobEffect> TANGLING_NIGHTMARE =
      register("tangling_nightmare", new TanglingNightmareStatusEffect());
  public static final Holder<MobEffect> TANGLING_DREAMS =
      register("tangling_dreams", new TanglingDreamsStatusEffect());
  public static final Holder<MobEffect> TARGETED =
      register("targeted", new TargetedStatusEffect());
  public static final Holder<MobEffect> VOICELESS =
      register("voiceless", new AnnoyingStatusEffect());
  public static final Holder<MobEffect> VULNERABLE =
      register("vulnerable", new VulnerableStatusEffect());
  public static final Holder<MobEffect> WATER_FILLING =
      register("water_filling", new WaterFillingStatusEffect());

  public static final Map<Holder<MobEffect>, Map<String, Integer>> STATUS_EFFECT_MAP =
      new LinkedHashMap<>();

  public static void initialize() {
    configure(ANOREXIA, paraMap(200, 200));
    configure(CARROT_CURSE, paraMap(60, 1200));
    configure(CHANNELING, paraMap(20, 100));
    configure(CHAOTIC_EXPLOSION, paraMap(15, 600, 1));
    configure(CHAOTIC_TELEPORTATION, paraMap(5, 200));
    configure(CONTROLS_ALWAYS_DIG, paraMap(30, 300, null, 5, 600, null));
    configure(CONTROLS_ALWAYS_JUMP, paraMap(30, 300, null, 15, 600, null));
    configure(CONTROLS_CHAOTIC_USE, paraMap(120, 600, 1, 30, null, 0));
    configure(CONTROLS_CRAB, paraMap(120, 600));
    configure(CONTROLS_MIRROR, paraMap(120, 600, null, 30, null, null));
    configure(CONTROLS_SHORT_REACH, paraMap(120, 600, 1, 30, null, 0));
    configure(CRAWLER, paraMap(60, 600));
    configure(CREEPERPHOBIA, paraMap(120, 600, null, 60, null, null));
    configure(CURSE_OF_VANISHING, paraMap(50, 100, null, 25, null, null));
    configure(DISABLE_CRAFTING_TABLE, paraMap(60, 600, null, 10, 600, 0));
    configure(DISABLE_INVENTORY, paraMap(10, 100, null, 10, null, null));
    configure(DISABLE_SLEEPING, paraMap(120, 600));
    configure(ENDERMAN_HOSTILE, paraMap(120, 600));
    configure(HEAVINESS, paraMap(60, 600));
    configure(HORSELESS, paraMap(5, 300));
    configure(MISFORTUNE, paraMap(-1, -1));
    configure(OPPRESSED, paraMap(10, 600, 2, 10, null, 0));
    configure(REALLY_COLD, paraMap(3, 120));
    configure(REALLY_HOT, paraMap(3, 120));
    configure(REPEATER, paraMap(120, 600, null, 60, null, null));
    configure(SCHIZOPHRENIA, paraMap(-1, -1));
    configure(SLIPPY, paraMap(120, 600));
    configure(SPIN, paraMap(5, 300, 4, 3, 600, 2));
    configure(SWAPPING_HANDS, paraMap(10, 200, null));
    configure(TARGETED, paraMap(120, 600));
    configure(VOICELESS, paraMap(120, 600));
    configure(VULNERABLE, paraMap(120, 600, null, 30, null, null));
    configure(WATER_FILLING, paraMap(0, 15, null, -1, null, null));
    configure(BLINDNESS, paraMap(30, 600));
    configure(DARKNESS, paraMap(30, 300, null, 15, 600, null));
    configure(HUNGER, paraMap(60, 600, null, 30, 1200, null));
    configure(INFESTED, paraMap(120, 600));
    configure(LEVITATION, paraMap(10, 200));
    configure(MINING_FATIGUE, paraMap(60, 600, null, 30, null, null));
    configure(NAUSEA, paraMap(10, 200));
    configure(SLOWNESS, paraMap(60, 600, 1, 30, null, 0));
    configure(WEAKNESS, paraMap(120, 600, 1, 30, null, 0));
    configure(WITHER, paraMap(10, 600));
    config.save();
  }

  private static Holder<MobEffect> register(String name, MobEffect effect) {
    return Registry.registerForHolder(
        BuiltInRegistries.MOB_EFFECT, Identifier.fromNamespaceAndPath(MOD_ID, name), effect);
  }

  private static void configure(Holder<MobEffect> effect, Map<String, Integer> defaults) {
    String name = BuiltInRegistries.MOB_EFFECT.getKey(effect.value()).getPath();
    STATUS_EFFECT_MAP.put(effect, config.getStatusParameters(name, defaults));
  }

  private static Map<String, Integer> paraMap(Integer duration, Integer interval) {
    return paraMap(duration, interval, null);
  }

  private static Map<String, Integer> paraMap(
      Integer duration, Integer interval, @Nullable Integer amplifier) {
    return paraMap(duration, interval, amplifier, null, null, null);
  }

  private static Map<String, Integer> paraMap(
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
