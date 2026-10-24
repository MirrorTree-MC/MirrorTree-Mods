package top.bearcabbage.mirrorskills.data;

import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.ByteBufUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
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
    private Map<String, Boolean> enabledSkills = Map.of();
    private Map<String, Long> cooldowns = Map.of();
    // For Potion Time: "harmful_reduction" -> x, "beneficial_increase" -> y
    // For Potion Level: "level_adjustment" -> x
    private Map<String, Float> parameters = Map.of();
    private boolean sharedSkills = true, sharedCooldowns = true, sharedParameters = true;
    
    private String lastDeathDimension = null; // Stores identifier string e.g. "minecraft:overworld"
    private Vec3 lastDeathLocation = null;

    public boolean isSkillEnabled(String skillId) {
        return enabledSkills.getOrDefault(skillId, false);
    }

    public void setSkillEnabled(String skillId, boolean enabled) {
        if (sharedSkills) {
            enabledSkills = new HashMap<>(enabledSkills);
            sharedSkills = false;
        }
        enabledSkills.put(skillId, enabled);
    }

    public long getCooldown(String skillId) {
        return cooldowns.getOrDefault(skillId, 0L);
    }

    public void setCooldown(String skillId, long timestamp) {
        if (sharedCooldowns) {
            cooldowns = new HashMap<>(cooldowns);
            sharedCooldowns = false;
        }
        cooldowns.put(skillId, timestamp);
    }

    public float getParameter(String key) {
        return parameters.getOrDefault(key, 0.0f);
    }

    public void setParameter(String key, float value) {
        if (sharedParameters) {
            parameters = new HashMap<>(parameters);
            sharedParameters = false;
        }
        parameters.put(key, value);
    }
    
    public void addParameter(String key, float value) {
        setParameter(key, getParameter(key) + value);
    }

    public Vec3 getLastDeathLocation() {
        return lastDeathLocation;
    }
    
    public String getLastDeathDimension() {
        return lastDeathDimension;
    }

    public boolean hasDeathLocation() {
        return lastDeathLocation != null;
    }

    public void setLastDeathLocation(Vec3 location, String dimension) {
        this.lastDeathLocation = location;
        this.lastDeathDimension = dimension;
    }

    public void clearDeathLocation() {
        this.lastDeathLocation = null;
        this.lastDeathDimension = null;
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
        if (lastDeathLocation != null) {
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
                data.setSkillEnabled(key, skillsTag.getBooleanOr(key, false));
            }
        }

        if (nbt.contains("Cooldowns")) {
            CompoundTag cooldownsTag = nbt.getCompoundOrEmpty("Cooldowns");
            for (String key : cooldownsTag.keySet()) {
                data.setCooldown(key, cooldownsTag.getLongOr(key, 0L));
            }
        }

        if (nbt.contains("Parameters")) {
            CompoundTag paramsTag = nbt.getCompoundOrEmpty("Parameters");
            for (String key : paramsTag.keySet()) {
                data.setParameter(key, paramsTag.getFloatOr(key, 0f));
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
        }

        return data;
    }

    public SkillData copy() {
        SkillData newData = new SkillData();
        newData.enabledSkills = this.enabledSkills;
        newData.cooldowns = this.cooldowns;
        newData.parameters = this.parameters;
        sharedSkills = sharedCooldowns = sharedParameters = true;
        newData.lastDeathLocation = this.lastDeathLocation;
        newData.lastDeathDimension = this.lastDeathDimension;
        return newData;
    }

    public static final com.mojang.serialization.Codec<SkillData> CODEC = CompoundTag.CODEC.xmap(SkillData::fromNbt, data -> data.toNbt(new CompoundTag()));

    private static String readNetworkString(FriendlyByteBuf buffer) {
        return buffer.readUtf(65535);
    }

    private static void writeNetworkString(FriendlyByteBuf buffer, String value) {
        // Write directly into the packet instead of allocating a temporary Netty buffer per key.
        buffer.writeVarInt(ByteBufUtil.utf8Bytes(value));
        ByteBufUtil.writeUtf8(buffer.unwrap(), value);
    }

    // Network snapshots retain every field and the original tick cadence, without building NBT trees.
    // Persistent worlds continue to use CODEC above.
    public static final StreamCodec<FriendlyByteBuf, SkillData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SkillData decode(FriendlyByteBuf buffer) {
            SkillData data = new SkillData();
            data.enabledSkills = buffer.readMap(SkillData::readNetworkString, FriendlyByteBuf::readBoolean);
            data.cooldowns = buffer.readMap(SkillData::readNetworkString, FriendlyByteBuf::readVarLong);
            data.parameters = buffer.readMap(SkillData::readNetworkString, FriendlyByteBuf::readFloat);
            data.sharedSkills = data.sharedCooldowns = data.sharedParameters = false;
            if (buffer.readBoolean()) {
                data.lastDeathLocation = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
                data.lastDeathDimension = buffer.readNullable(SkillData::readNetworkString);
            }
            return data;
        }

        @Override
        public void encode(FriendlyByteBuf buffer, SkillData data) {
            buffer.writeMap(data.enabledSkills, SkillData::writeNetworkString, FriendlyByteBuf::writeBoolean);
            buffer.writeMap(data.cooldowns, SkillData::writeNetworkString, FriendlyByteBuf::writeVarLong);
            buffer.writeMap(data.parameters, SkillData::writeNetworkString, FriendlyByteBuf::writeFloat);
            buffer.writeBoolean(data.lastDeathLocation != null);
            if (data.lastDeathLocation != null) {
                buffer.writeDouble(data.lastDeathLocation.x);
                buffer.writeDouble(data.lastDeathLocation.y);
                buffer.writeDouble(data.lastDeathLocation.z);
                buffer.writeNullable(data.lastDeathDimension, SkillData::writeNetworkString);
            }
        }
    };
}
