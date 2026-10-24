package top.bearcabbage.annoyingeffects.effect;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.player.Player;
import top.bearcabbage.annoyingeffects.AnnoyingEffects;
import top.bearcabbage.annoyingeffects.StatusEffectInstanceStackHolder;
import top.bearcabbage.annoyingeffects.utils.NoSavePlayerData;
import top.bearcabbage.lanterninstorm.network.StormNetworking;

/** Keeps the original player buildup curve and inverse-interval weighted effect selection. */
public class TanglingNightmareStatusEffect extends MobEffect {
  public static final NoSavePlayerData<Double> AnnoyingBar = new NoSavePlayerData<>(0.0);

  private record Candidate(Holder<MobEffect> effect, int duration, int amplifier, int interval) {}

  public TanglingNightmareStatusEffect() {
    super(MobEffectCategory.HARMFUL, 0);
  }

  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return true;
  }

  @Override
  public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int strength) {
    // Persisted nightmares must not generate debuffs before the first safety tick.
    if (entity instanceof Player player
        && level.dimension().identifier().toString().equals("mirrortree:bedroom")) {
      AnnoyingBar.reset(player);
      return false;
    }
    boolean weak = this instanceof TanglingDreamsStatusEffect;
    double factor = Math.scalb(1.0, Math.min(30, strength));
    var random = RandomSource.create(entity.getRandom().nextLong());
    if (entity instanceof Player player) {
      int count = 0;
      for (var effect : entity.getActiveEffects())
        if (effect.getEffect().value() instanceof AnnoyingStatusEffect) count++;
      double progress =
          AnnoyingBar.get(player)
              + (weak ? 1.0 / 300 : 1.0 / 100) * Math.pow(weak ? .5 : .8, count) * factor;
      if (player instanceof ServerPlayer serverPlayer)
        StormNetworking.bar(serverPlayer, true, (int) (progress * 6));
      if (progress < 1) {
        AnnoyingBar.set(player, progress);
        return true;
      }
      AnnoyingBar.reset(player);
      List<Candidate> eligible = new ArrayList<>();
      double total = 0;
      for (var entry : AnnoyingEffects.STATUS_EFFECT_MAP.entrySet()) {
        Candidate candidate =
            candidate(level, entity, entry.getKey(), entry.getValue(), weak, strength);
        if (candidate != null) {
          eligible.add(candidate);
          total += 1.0 / candidate.interval;
        }
      }
      double dice = random.nextDouble() * total;
      for (Candidate candidate : eligible) {
        dice -= 1.0 / candidate.interval;
        if (dice <= 0) {
          queue(entity, candidate);
          break;
        }
      }
    } else {
      for (var entry : AnnoyingEffects.STATUS_EFFECT_MAP.entrySet()) {
        Candidate candidate =
            candidate(level, entity, entry.getKey(), entry.getValue(), weak, strength);
        if (candidate != null && random.nextInt(candidate.interval) < factor) {
          queue(entity, candidate);
          break;
        }
      }
    }
    return true;
  }

  private static void queue(LivingEntity entity, Candidate candidate) {
    ((StatusEffectInstanceStackHolder) entity)
        .pushStatusEffect(
            new MobEffectInstance(
                candidate.effect, candidate.duration, candidate.amplifier, true, true));
  }

  private static Candidate candidate(
      ServerLevel level,
      LivingEntity entity,
      Holder<MobEffect> effect,
      Map<String, Integer> rule,
      boolean weak,
      int strength) {
    if (entity.hasEffect(effect)) return null;
    int seconds = rule.get(weak ? "weak_duration" : "duration");
    if (seconds < 0) return null;
    int duration = (int) Math.min(Integer.MAX_VALUE, seconds * 20L);
    int interval = (int) Math.min(Integer.MAX_VALUE, rule.get(weak ? "weak_interval" : "interval") * 20L);
    if (effect.equals(AnnoyingEffects.CHANNELING) && !level.isThundering()) return null;
    if (effect.equals(AnnoyingEffects.HORSELESS) && entity.getVehicle() instanceof Horse)
      interval = Math.max(1, interval / 5);
    if (entity instanceof Player player) {
      if (!weak && effect.equals(AnnoyingEffects.WATER_FILLING)) {
        int water = WaterFillingStatusEffect.WaterTicks.get(player);
        if (!entity.isInWaterOrRain() || water < 1500) return null;
        duration = water / 5;
      }
      if (effect.equals(AnnoyingEffects.CARROT_CURSE)
          && CarrotCurseStatusEffect.CarrotTicks.get(player) > 0) return null;
    }
    if (duration <= 0 || interval <= 0) return null;
    return new Candidate(
        effect, duration, rule.get(weak ? "weak_amplifier" : "amplifier") + Math.max(0, strength / 31), interval);
  }
}
