package top.bearcabbage.mirrorskills.data;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

public class SkillData {
    // Skill IDs
    public static final String SKILL_INVISIBILITY = "invisibility";
    public static final String SKILL_DASHING = "dashing";
    public static final String SKILL_HEALING = "healing";
    public static final String SKILL_DEATH_FLASHBACK = "death_flashback";
    public static final String SKILL_POTION_TIME = "potion_time";
    public static final String SKILL_POTION_LEVEL = "potion_level";
    public static final String SKILL_PERIODIC_ABSORPTION = "periodic_absorption";
    public static final String SKILL_BOW_FAST_CHARGE = "bow_fast_charge";
    public static final String SKILL_BOW_DYNAMIC_CRIT = "bow_dynamic_crit";
    public static final String SKILL_CRIT_MULTIPLIER = "crit_multiplier_skill"; // "crit_multiplier" used for param
    public static final String SKILL_TIPPED_ARROW = "tipped_arrow";
    public static final String SKILL_INVINCIBLE = "invincible";

    // Data Storage
    private final Map<String, Boolean> enabledSkills = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    // For Potion Time: "harmful_reduction" -> x, "beneficial_increase" -> y
    // For Potion Level: "level_adjustment" -> x
    private final Map<String, Float> parameters = new HashMap<>();
    
    private String lastDeathDimension = null; // Stores identifier string e.g. "minecraft:overworld"
    private Vec3 lastDeathLocation = null;
    private boolean hasDeathLocation = false;

    // Default Constructor
    public SkillData() {}

    public boolean isSkillEnabled(String skillId) {
        return enabledSkills.getOrDefault(skillId, false);
    }

    public void setSkillEnabled(String skillId, boolean enabled) {
        enabledSkills.put(skillId, enabled);
    }

    public long getCooldown(String skillId) {
        return cooldowns.getOrDefault(skillId, 0L);
    }

    public void setCooldown(String skillId, long timestamp) {
        cooldowns.put(skillId, timestamp);
    }

    public float getParameter(String key) {
        return parameters.getOrDefault(key, 0.0f);
    }

    public void setParameter(String key, float value) {
        parameters.put(key, value);
    }
    
    public void addParameter(String key, float value) {
        parameters.put(key, parameters.getOrDefault(key, 0.0f) + value);
    }

    public Vec3 getLastDeathLocation() {
        return lastDeathLocation;
    }
    
    public String getLastDeathDimension() {
        return lastDeathDimension;
    }

    public boolean hasDeathLocation() {
        return hasDeathLocation;
    }

    public void setLastDeathLocation(Vec3 location, String dimension) {
        this.lastDeathLocation = location;
        this.lastDeathDimension = dimension;
        this.hasDeathLocation = location != null;
    }

    public void clearDeathLocation() {
        this.lastDeathLocation = null;
        this.lastDeathDimension = null;
        this.hasDeathLocation = false;
    }

    // Serialization
    public CompoundTag toNbt(CompoundTag nbt) {
        // Enabled Skills
        CompoundTag skillsTag = new CompoundTag();
        enabledSkills.forEach((k, v) -> skillsTag.putBoolean(k, v));
        nbt.put("EnabledSkills", skillsTag);

        // Cooldowns
        CompoundTag cooldownsTag = new CompoundTag();
        cooldowns.forEach((k, v) -> cooldownsTag.putLong(k, v));
        nbt.put("Cooldowns", cooldownsTag);

        // Parameters
        CompoundTag paramsTag = new CompoundTag();
        parameters.forEach((k, v) -> paramsTag.putFloat(k, v));
        nbt.put("Parameters", paramsTag);

        // Death Location
        if (hasDeathLocation && lastDeathLocation != null) {
            CompoundTag deathTag = new CompoundTag();
            deathTag.putDouble("x", lastDeathLocation.x);
            deathTag.putDouble("y", lastDeathLocation.y);
            deathTag.putDouble("z", lastDeathLocation.z);
            if (lastDeathDimension != null) {
                deathTag.putString("dim", lastDeathDimension);
            }
            nbt.put("DeathLocation", deathTag);
        }

        return nbt;
    }

    public static SkillData fromNbt(CompoundTag nbt) {
        SkillData data = new SkillData();

        if (nbt.contains("EnabledSkills")) {
            CompoundTag skillsTag = nbt.getCompoundOrEmpty("EnabledSkills");
            for (String key : skillsTag.keySet()) {
                data.enabledSkills.put(key, skillsTag.getBooleanOr(key, false));
            }
        }

        if (nbt.contains("Cooldowns")) {
            CompoundTag cooldownsTag = nbt.getCompoundOrEmpty("Cooldowns");
            for (String key : cooldownsTag.keySet()) {
                data.cooldowns.put(key, cooldownsTag.getLongOr(key, 0L));
            }
        }

        if (nbt.contains("Parameters")) {
            CompoundTag paramsTag = nbt.getCompoundOrEmpty("Parameters");
            for (String key : paramsTag.keySet()) {
                data.parameters.put(key, paramsTag.getFloatOr(key, 0f));
            }
        }

        if (nbt.contains("DeathLocation")) {
            CompoundTag deathTag = nbt.getCompoundOrEmpty("DeathLocation");
            data.lastDeathLocation = new Vec3(
                    deathTag.getDoubleOr("x", 0d),
                    deathTag.getDoubleOr("y", 0d),
                    deathTag.getDoubleOr("z", 0d)
            );
            if (deathTag.contains("dim")) {
                data.lastDeathDimension = deathTag.getStringOr("dim", "");
            }
            data.hasDeathLocation = true;
        }

        return data;
    }

    public SkillData copy() {
        SkillData newData = new SkillData();
        newData.enabledSkills.putAll(this.enabledSkills);
        newData.cooldowns.putAll(this.cooldowns);
        newData.parameters.putAll(this.parameters);
        newData.lastDeathLocation = this.lastDeathLocation;
        newData.lastDeathDimension = this.lastDeathDimension;
        newData.hasDeathLocation = this.hasDeathLocation;
        return newData;
    }

    public static final com.mojang.serialization.Codec<SkillData> CODEC = CompoundTag.CODEC.xmap(SkillData::fromNbt, data -> data.toNbt(new CompoundTag()));
}
