package top.bearcabbage.lanterninstorm.content;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class BorderParticles {
  public static final SimpleParticleType BORDER =
      Registry.register(
          BuiltInRegistries.PARTICLE_TYPE, StormContent.id("border"), FabricParticleTypes.simple());

  public static void initialize() {}

  public static void box(Level level, Vec3 center, int radius, boolean soul) {
    if (!level.isClientSide()) return;
    int steps = soul ? 30 : Math.max(1, radius);
    var random = level.getRandom();
    double vx = random.nextDouble() * .2 - .1,
        vy = random.nextDouble() * .2 - .1,
        vz = random.nextDouble() * .2 - .1;
    for (int axis = 0; axis < 3; axis++)
      for (int a = -1; a <= 1; a += 2)
        for (int b = -1; b <= 1; b += 2)
          for (int n = 0; n <= steps; n++) {
            double t = -radius + 2.0 * radius * n / steps;
            double x = axis == 0 ? t : a * radius,
                y = axis == 1 ? t : (axis == 0 ? a : b) * radius,
                z = axis == 2 ? t : b * radius;
            level.addParticle(
                soul ? ParticleTypes.SOUL_FIRE_FLAME : BORDER,
                center.x() + x,
                center.y() + y,
                center.z() + z,
                vx,
                vy,
                vz);
          }
  }
}
