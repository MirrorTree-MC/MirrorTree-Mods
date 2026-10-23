package top.bearcabbage.lanterninstorm.progression;

import net.minecraft.resources.Identifier;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.api.experience.source.*;
import net.puffish.skillsmod.api.util.*;

public record SpiritExperienceSource(String type) implements ExperienceSource {
  public static void register() {
    SkillsAPI.registerExperienceSource(
        Identifier.fromNamespaceAndPath("spirit-in-storm", "experience"),
        SpiritExperienceSource::parse);
  }

  private static Result<SpiritExperienceSource, Problem> parse(
      ExperienceSourceConfigContext context) {
    return context
        .getData()
        .andThen(net.puffish.skillsmod.api.json.JsonElement::getAsObject)
        .andThen(json -> json.getString("type"))
        .mapSuccess(SpiritExperienceSource::new);
  }

  @Override
  public void dispose(ExperienceSourceDisposeContext context) {}
}
