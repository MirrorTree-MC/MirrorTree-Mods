package top.bearcabbage.lanterninstorm.test;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.puffish.skillsmod.api.SkillsAPI;
import top.bearcabbage.lanterninstorm.progression.ProgressionService;
import top.bearcabbage.mirrorskills.MirrorSkills;
import top.bearcabbage.mirrorskills.data.SkillData;

public class SkillsServerGameTest {
  @GameTest(maxTicks = 200)
  public void embeddedTreesAndRewards(GameTestHelper helper) {
    var player = StormServerGameTest.player(helper.getLevel(), "skills-test");
    try {
      var combat = SkillsAPI.getCategory(Identifier.parse("mirrortree:combat")).orElseThrow();
      var support = SkillsAPI.getCategory(Identifier.parse("mirrortree:support")).orElseThrow();
      helper.assertTrue(combat.streamSkills().count() == 16, "16 embedded combat nodes");
      helper.assertTrue(support.streamSkills().count() == 42, "42 embedded support nodes");
      var health = combat.getSkill("health_1").orElseThrow();
      double baseHealth = player.getAttributeValue(Attributes.MAX_HEALTH);
      health.unlock(player);
      helper.assertTrue(player.getAttributeValue(Attributes.MAX_HEALTH) == baseHealth + 4,
          "embedded attribute reward applies");
      health.lock(player);
      helper.assertTrue(player.getAttributeValue(Attributes.MAX_HEALTH) == baseHealth,
          "locking removes attribute reward");
      var invincible = combat.getSkill("invincible").orElseThrow();
      invincible.unlock(player);
      var target = (AttachmentTarget) player;
      helper.assertTrue(target.getAttachedOrCreate(MirrorSkills.SKILL_DATA)
          .isSkillEnabled(SkillData.SKILL_INVINCIBLE), "legacy grant command reward runs");
      combat.getSkill("invincible_cd_reduction").orElseThrow().unlock(player);
      helper.assertTrue(target.getAttachedOrCreate(MirrorSkills.SKILL_DATA)
          .getParameter("invincible_cd") == 90, "legacy parameter command reward runs");
      invincible.lock(player);
      helper.assertTrue(!target.getAttachedOrCreate(MirrorSkills.SKILL_DATA)
          .isSkillEnabled(SkillData.SKILL_INVINCIBLE), "legacy revoke command reward runs");
      var experience = support.getExperience().orElseThrow();
      int before = experience.getTotal(player);
      ProgressionService.award(player, "food", "skills_test:food", 1);
      ProgressionService.award(player, "biome", "skills_test:biome", 1);
      helper.assertTrue(experience.getTotal(player) - before == 41,
          "LIStorm food and biome experience reaches embedded support tree once");

      var data = target.getAttachedOrCreate(MirrorSkills.SKILL_DATA).copy();
      data.setSkillEnabled(SkillData.SKILL_HEALING, true);
      data.setSkillEnabled(SkillData.SKILL_PERIODIC_ABSORPTION, true);
      data.setParameter("periodic_timer", 11999);
      data.setParameter("invincible_ticks", 2);
      target.setAttached(MirrorSkills.SKILL_DATA, data);
      player.setHealth(6);
      long currentTime = player.level().getGameTime();
      ServerTickEvents.END_LEVEL_TICK.invoker().onEndTick(player.level());
      data = target.getAttachedOrCreate(MirrorSkills.SKILL_DATA);
      helper.assertTrue(player.getHealth() == 16
              && data.getCooldown(SkillData.SKILL_HEALING) == currentTime,
          "combined skill tick heals and retains the healing cooldown");
      var absorption = player.getEffect(MobEffects.ABSORPTION);
      helper.assertTrue(data.getParameter("periodic_timer") == 0
              && absorption != null && absorption.isInfiniteDuration(),
          "combined skill tick resets expired periodic timer and grants absorption");
      helper.assertTrue(data.getParameter("invincible_ticks") == 1,
          "combined skill tick also retains the invincibility countdown");
      ServerTickEvents.END_LEVEL_TICK.invoker().onEndTick(player.level());
      data = target.getAttachedOrCreate(MirrorSkills.SKILL_DATA);
      helper.assertTrue(player.getHealth() == 16
              && data.getCooldown(SkillData.SKILL_HEALING) == currentTime
              && data.getParameter("periodic_timer") == 1
              && data.getParameter("invincible_ticks") == 0,
          "next skill tick advances periodic timer and ends invincibility without losing cooldown");

      data = data.copy();
      data.setSkillEnabled(SkillData.SKILL_POTION_TIME, true);
      data.setSkillEnabled(SkillData.SKILL_POTION_LEVEL, true);
      data.setParameter("beneficial_increase", .5f);
      data.setParameter("level_adjustment", 1);
      target.setAttached(MirrorSkills.SKILL_DATA, data);
      player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
      var effect = player.getEffect(MobEffects.REGENERATION);
      helper.assertTrue(effect.getDuration() == 300 && effect.getAmplifier() == 1,
          "merged mixins apply potion bonuses once");
      var copy = new MobEffectInstance(effect);
      var encoded = MobEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE, copy).getOrThrow();
      var restored = MobEffectInstance.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
      player.removeEffect(MobEffects.REGENERATION);
      player.addEffect(restored);
      effect = player.getEffect(MobEffects.REGENERATION);
      helper.assertTrue(effect.getDuration() == 300 && effect.getAmplifier() == 1,
          "copy and persisted effect markers prevent repeated potion bonuses");
      helper.succeed();
    } finally {
      helper.getLevel().getServer().getPlayerList().remove(player);
    }
  }
}
