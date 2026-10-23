package top.bearcabbage.mirrorskills;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.phys.Vec3;
import top.bearcabbage.mirrorskills.data.SkillData;
public final class SkillDataCheck {
 public static void main(String[] args){
  SkillData data=new SkillData();
  data.setSkillEnabled("invisibility",true);data.setParameter("beneficial_increase",0.6f);
  data.setCooldown("invisibility",1234567890123L);data.setLastDeathLocation(new Vec3(3.5,80,-5.5),"minecraft:the_nether");
  var encoded=SkillData.CODEC.encodeStart(NbtOps.INSTANCE,data).getOrThrow();
  var restored=SkillData.CODEC.parse(NbtOps.INSTANCE,encoded).getOrThrow();
  if(!restored.isSkillEnabled("invisibility")||restored.getParameter("beneficial_increase")!=0.6f||restored.getCooldown("invisibility")!=1234567890123L||!restored.getLastDeathDimension().equals("minecraft:the_nether")||!restored.getLastDeathLocation().equals(new Vec3(3.5,80,-5.5)))throw new AssertionError("persistence");
  var copy=restored.copy();copy.setSkillEnabled("invisibility",false);copy.clearDeathLocation();
  if(!restored.isSkillEnabled("invisibility")||!restored.hasDeathLocation())throw new AssertionError("copy aliases original");
  var empty=SkillData.fromNbt(new CompoundTag());
  if(empty.hasDeathLocation()||empty.getParameter("missing")!=0||empty.isSkillEnabled("missing"))throw new AssertionError("legacy defaults");
  System.out.println("PASS: skill flags, parameters, long cooldown, cross-dimension death location, codec round trip, copy isolation and legacy defaults");
 }
}
