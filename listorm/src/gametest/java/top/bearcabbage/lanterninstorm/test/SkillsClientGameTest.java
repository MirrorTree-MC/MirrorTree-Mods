package top.bearcabbage.lanterninstorm.test;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.puffish.skillsmod.api.SkillsAPI;
import org.lwjgl.glfw.GLFW;
import top.bearcabbage.mirrorskills.MirrorSkills;
import top.bearcabbage.mirrorskills.data.SkillData;

public final class SkillsClientGameTest implements FabricClientGameTest {
  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  @Override
  public void runTest(ClientGameTestContext context) {
    TestWorldSave save;
    long[] cooldown = {0};
    try (var world = context.worldBuilder().create()) {
      save = world.getWorldSave();
      world.getServer().runOnServer(server -> {
        var player = server.getPlayerList().getPlayers().getFirst();
        SkillsAPI.getCategory(Identifier.parse("mirrortree:combat")).orElseThrow()
            .getSkill("invincible").orElseThrow().unlock(player);
        var target = (AttachmentTarget) player;
        var data = target.getAttachedOrCreate(MirrorSkills.SKILL_DATA).copy();
        data.setLastDeathLocation(new Vec3(12.5, 80, -24.5), "mirrortree:bedroom");
        data.setSkillEnabled(SkillData.SKILL_PERIODIC_ABSORPTION, true);
        data.setParameter("periodic_timer", 100);
        target.setAttached(MirrorSkills.SKILL_DATA, data);
      });
      context.waitFor(client -> client.player != null
          && ((AttachmentTarget) client.player).getAttached(MirrorSkills.SKILL_DATA) != null
          && ((AttachmentTarget) client.player).getAttached(MirrorSkills.SKILL_DATA)
              .isSkillEnabled(SkillData.SKILL_INVINCIBLE));
      context.getInput().pressKey(GLFW.GLFW_KEY_GRAVE_ACCENT);
      context.waitFor(client -> ((AttachmentTarget) client.player)
          .getAttached(MirrorSkills.SKILL_DATA).getParameter("invincible_ticks") > 0);
      world.getServer().runOnServer(server -> {
        var data = ((AttachmentTarget) server.getPlayerList().getPlayers().getFirst())
            .getAttachedOrCreate(MirrorSkills.SKILL_DATA);
        cooldown[0] = data.getCooldown(SkillData.SKILL_INVINCIBLE);
        check(cooldown[0] > 0 && data.getParameter("invincible_ticks") > 0,
            "registered key sends legacy payload and synchronizes active HUD timer");
        check(data.getParameter("periodic_timer") > 100,
            "periodic timer advances while invincibility is active");
      });
      context.getInput().pressKey(GLFW.GLFW_KEY_GRAVE_ACCENT);
      context.waitTicks(5);
      context.runOnClient(client -> {
        var data = ((AttachmentTarget) client.player).getAttached(MirrorSkills.SKILL_DATA);
        check(data.getParameter("periodic_timer") > 100
                && data.getParameter("invincible_ticks") > 0,
            "one synchronized snapshot contains both periodic timer and active countdown");
      });
      world.getServer().runOnServer(server -> check(
          ((AttachmentTarget) server.getPlayerList().getPlayers().getFirst())
              .getAttachedOrCreate(MirrorSkills.SKILL_DATA).getCooldown(SkillData.SKILL_INVINCIBLE)
              == cooldown[0], "repeated key press respects cooldown"));
      world.getServer().runOnServer(server -> {
        var target = (AttachmentTarget) server.getPlayerList().getPlayers().getFirst();
        var data = target.getAttachedOrCreate(MirrorSkills.SKILL_DATA).copy();
        // Leave enough fixture time to observe the persisted countdown after reopening.
        data.setParameter("invincible_ticks", 1000);
        target.setAttached(MirrorSkills.SKILL_DATA, data);
      });
    }
    try (var world = save.open()) {
      world.getServer().runOnServer(server -> {
        var player = server.getPlayerList().getPlayers().getFirst();
        var data = ((AttachmentTarget) player).getAttachedOrCreate(MirrorSkills.SKILL_DATA);
        check(data.isSkillEnabled(SkillData.SKILL_INVINCIBLE), "skill survives world reopen");
        check(data.getCooldown(SkillData.SKILL_INVINCIBLE) == cooldown[0],
            "cooldown survives world reopen");
        check(data.isSkillEnabled(SkillData.SKILL_PERIODIC_ABSORPTION)
                && data.getParameter("periodic_timer") > 100
                && data.getParameter("invincible_ticks") > 0
                && data.getParameter("invincible_ticks") <= 1000,
            "periodic timer and active countdown both survive world reopen");
        check("mirrortree:bedroom".equals(data.getLastDeathDimension())
            && new Vec3(12.5, 80, -24.5).equals(data.getLastDeathLocation()),
            "cross-dimension death location survives world reopen");
        check(SkillsAPI.getCategory(Identifier.parse("mirrortree:combat")).orElseThrow()
            .streamUnlockedSkills(player).anyMatch(skill -> skill.getId().equals("invincible")),
            "Pufferfish node progress survives world reopen under original category ID");
      });
    }
  }
}
