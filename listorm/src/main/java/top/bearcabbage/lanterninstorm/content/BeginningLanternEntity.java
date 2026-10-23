package top.bearcabbage.lanterninstorm.content;

import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;

public final class BeginningLanternEntity extends Entity {
  public static final EntityType<BeginningLanternEntity> BEGINNING = register("beginning_lantern");
  public static final EntityType<BeginningLanternEntity> WARP = register("warp_beginning_lantern");
  public int age;

  private static EntityType<BeginningLanternEntity> register(String path) {
    ResourceKey<EntityType<?>> key =
        ResourceKey.create(
            Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("lanterninstorm", path));
    return Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        key,
        EntityType.Builder.<BeginningLanternEntity>of(BeginningLanternEntity::new, MobCategory.MISC)
            .fireImmune()
            .sized(2, 2)
            .clientTrackingRange(64)
            .updateInterval(Integer.MAX_VALUE)
            .build(key));
  }

  public BeginningLanternEntity(EntityType<? extends BeginningLanternEntity> type, Level level) {
    super(type, level);
    age = random.nextInt(100000);
  }

  public static void initialize() {}

  public static void create(ServerLevel level, BlockPos pos, String name, boolean warp) {
    var entity = new BeginningLanternEntity(warp ? WARP : BEGINNING, level);
    entity.setPos(net.minecraft.world.phys.Vec3.atCenterOf(pos));
    entity.setCustomName(Component.literal(name));
    level.addFreshEntity(entity);
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {}

  @Override
  protected void readAdditionalSaveData(ValueInput input) {}

  @Override
  protected void addAdditionalSaveData(ValueOutput output) {}

  @Override
  public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    return false;
  }

  @Override
  public void tick() {
    super.tick();
    age++;
    if (age % 30 == 0) BorderParticles.box(level(), position(), 16, true);
    if (getY() < level().getMinY() && !level().isClientSide()) discard();
  }
}
