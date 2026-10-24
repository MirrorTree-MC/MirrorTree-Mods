package top.bearcabbage.mirrorskills;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import top.bearcabbage.mirrorskills.data.SkillData;

public final class SkillDataCheck {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void checkNetworkRoundTrip(SkillData data) {
    var buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      SkillData.STREAM_CODEC.encode(buffer, data);
      var decoded = SkillData.STREAM_CODEC.decode(buffer);
      check(buffer.readableBytes() == 0
              && data.toNbt(new CompoundTag()).equals(decoded.toNbt(new CompoundTag())),
          "compact network snapshot preserves all persisted fields exactly");
      var copy = decoded.copy();
      decoded.addParameter("network_copy", 1);
      check(copy.getParameter("network_copy") == 0, "decoded snapshots also isolate later mutations");
    } finally {
      buffer.release();
    }
  }

  public static void main(String[] args) {
    SkillData data = new SkillData();
    data.setSkillEnabled("invisibility", true);
    data.setParameter("beneficial_increase", 0.6f);
    data.setCooldown("invisibility", 1234567890123L);
    data.setLastDeathLocation(new Vec3(3.5, 80, -5.5), "minecraft:the_nether");
    var encoded = SkillData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
    var restored = SkillData.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
    check(restored.isSkillEnabled("invisibility")
            && restored.getParameter("beneficial_increase") == 0.6f
            && restored.getCooldown("invisibility") == 1234567890123L
            && "minecraft:the_nether".equals(restored.getLastDeathDimension())
            && new Vec3(3.5, 80, -5.5).equals(restored.getLastDeathLocation()),
        "codec preserves flags, parameters, long cooldown and cross-dimension death location");

    var copy = restored.copy();
    copy.setSkillEnabled("invisibility", false);
    copy.setParameter("beneficial_increase", 1.5f);
    copy.setCooldown("invisibility", 42L);
    copy.clearDeathLocation();
    check(restored.isSkillEnabled("invisibility")
            && restored.getParameter("beneficial_increase") == 0.6f
            && restored.getCooldown("invisibility") == 1234567890123L
            && restored.hasDeathLocation(),
        "copy mutations leave all original maps and death location unchanged");
    check(!copy.hasDeathLocation() && copy.getLastDeathDimension() == null
            && !copy.toNbt(new CompoundTag()).contains("DeathLocation"),
        "cleared death location is absent from saved data");

    var first = data.copy();
    var second = data.copy();
    data.setSkillEnabled("invisibility", false);
    data.setCooldown("invisibility", -42);
    data.addParameter("beneficial_increase", 0.4f);
    first.setParameter("beneficial_increase", 3);
    check(second.isSkillEnabled("invisibility")
            && second.getCooldown("invisibility") == 1234567890123L
            && second.getParameter("beneficial_increase") == 0.6f
            && data.getParameter("beneficial_increase") == 1,
        "mutating the source and sibling copies cannot change earlier snapshots");
    checkNetworkRoundTrip(data);
    checkNetworkRoundTrip(second);

    var empty = SkillData.fromNbt(new CompoundTag());
    check(!empty.hasDeathLocation() && empty.getLastDeathLocation() == null
            && empty.getLastDeathDimension() == null && empty.getParameter("missing") == 0
            && empty.getCooldown("missing") == 0 && !empty.isSkillEnabled("missing"),
        "empty legacy data retains defaults");
    checkNetworkRoundTrip(empty);

    var legacy = new CompoundTag();
    var location = new CompoundTag();
    location.putDouble("x", 7.5);
    location.putDouble("y", 64);
    location.putDouble("z", -2.5);
    legacy.put("DeathLocation", location);
    var legacyData = SkillData.fromNbt(legacy);
    check(legacyData.hasDeathLocation() && legacyData.getLastDeathDimension() == null
            && new Vec3(7.5, 64, -2.5).equals(legacyData.getLastDeathLocation()),
        "legacy death location without dimension stays available for current-world fallback");
    checkNetworkRoundTrip(legacyData);
    legacy.put("DeathLocation", new CompoundTag());
    var origin = SkillData.fromNbt(legacy);
    check(origin.hasDeathLocation() && Vec3.ZERO.equals(origin.getLastDeathLocation())
            && origin.getLastDeathDimension() == null,
        "legacy empty DeathLocation retains zero-coordinate defaults");
    legacyData.setLastDeathLocation(null, "minecraft:the_nether");
    check(!legacyData.hasDeathLocation()
            && !legacyData.toNbt(new CompoundTag()).contains("DeathLocation"),
        "null location is not persisted even when a dimension remains");
    checkNetworkRoundTrip(legacyData);
    data.setSkillEnabled("custom:future_skill", false);
    data.setCooldown("custom:negative", Long.MIN_VALUE);
    data.setCooldown("custom:positive", Long.MAX_VALUE);
    data.setParameter("custom:negative_zero", -0.0f);
    data.setParameter("custom:infinity", Float.POSITIVE_INFINITY);
    data.setParameter("custom:nan", Float.NaN);
    checkNetworkRoundTrip(data);
    System.out.println("PASS: persistent/network codecs, bidirectional copy isolation, arbitrary keys, numeric precision and legacy/null/cleared death locations");
  }
}
