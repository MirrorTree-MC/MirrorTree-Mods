package top.bearcabbage.annoyingeffects.effect;

/** Shared every-tick policy; passive and interval-based effects keep their own schedules. */
public abstract class TickingAnnoyingStatusEffect extends AnnoyingStatusEffect {
  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return true;
  }
}
