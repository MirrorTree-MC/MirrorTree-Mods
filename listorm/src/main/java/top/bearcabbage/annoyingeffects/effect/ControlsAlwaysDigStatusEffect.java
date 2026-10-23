package top.bearcabbage.annoyingeffects.effect;

public class ControlsAlwaysDigStatusEffect extends AnnoyingStatusEffect {
  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return true;
  }
}
