package top.bearcabbage.lanterninstorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import top.bearcabbage.lanterninstorm.content.BeginningLanternEntity;

public final class BeginningLanternRenderer
    extends EntityRenderer<BeginningLanternEntity, EntityRenderState> {
  private final ModelPart glass, cube;
  private final Identifier texture;

  public BeginningLanternRenderer(EntityRendererProvider.Context context, boolean warp) {
    super(context);
    shadowRadius = 0.5F;
    var mesh = new MeshDefinition();
    mesh.getRoot()
        .addOrReplaceChild(
            "glass",
            CubeListBuilder.create().texOffs(0, 0).addBox(-4, -4, -4, 8, 8, 8),
            PartPose.ZERO);
    mesh.getRoot()
        .addOrReplaceChild(
            "cube",
            CubeListBuilder.create().texOffs(32, 0).addBox(-4, -4, -4, 8, 8, 8),
            PartPose.ZERO);
    var root = LayerDefinition.create(mesh, 64, 32).bakeRoot();
    glass = root.getChild("glass");
    cube = root.getChild("cube");
    texture =
        Identifier.fromNamespaceAndPath(
            "lantern-in-storm",
            "textures/entity/" + (warp ? "warp_lantern_ring.png" : "lantern_ring.png"));
  }

  @Override
  public EntityRenderState createRenderState() {
    return new EntityRenderState();
  }

  @Override
  protected int getBlockLightLevel(BeginningLanternEntity entity, BlockPos pos) {
    return 15;
  }

  @Override
  protected boolean shouldShowName(BeginningLanternEntity entity, double distance) {
    return entity.hasCustomName() && distance < 4096;
  }

  @Override
  public void extractRenderState(
      BeginningLanternEntity entity, EntityRenderState state, float delta) {
    super.extractRenderState(entity, state, delta);
    state.ageInTicks = entity.age + delta;
  }

  @Override
  public void submit(
      EntityRenderState state,
      PoseStack pose,
      SubmitNodeCollector nodes,
      CameraRenderState camera) {
    pose.pushPose();
    pose.translate(0, 1, 0);
    pose.scale(3, 3, 3);
    float angle = state.ageInTicks * 3;
    float sine = (float) Math.sin(Math.PI / 4);
    pose.mulPose(Axis.YP.rotationDegrees(angle));
    pose.mulPose(new Quaternionf().setAngleAxis((float) Math.PI / 3, sine, 0, sine));
    for (int i = 0; i < 6; i++) {
      nodes.submitModelPart(
          (i & 1) == 0 ? glass : cube,
          pose,
          RenderTypes.entityCutout(texture),
          state.lightCoords,
          OverlayTexture.NO_OVERLAY,
          null);
      pose.scale(.875F, .875F, .875F);
      pose.mulPose(new Quaternionf().setAngleAxis((float) Math.PI / 3, sine, 0, sine));
      pose.mulPose(Axis.YP.rotationDegrees(angle));
      angle /= .875F;
    }
    pose.popPose();
    super.submit(state, pose, nodes, camera);
  }
}
