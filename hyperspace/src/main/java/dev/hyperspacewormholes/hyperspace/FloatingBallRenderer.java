package dev.hyperspacewormholes.hyperspace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws floating balls using Cobblemon's Poke Ball model, with a coloured glow and the targeting brackets when you're
 * aiming at one.
 */
public class FloatingBallRenderer extends EntityRenderer<FloatingBall> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/poke_balls/poke_ball.png");
    private static final ResourceLocation GOLDEN_TEXTURE = Hyperspace.id("textures/entity/golden_poke_ball.png");
    private static final float MODEL_SIZE = 0.5f;
    private static final float TILT = -20f;
    private static final float RADIUS = 0.95f;
    private static final float ARC = 0.9f;
    private static final float PIXEL = 0.0625f;
    private static final int PULSE = 2;
    private static final int GLOW_STEPS = 32;
    private static final float GLOW_INNER = 0.3f;
    private static final float GLOW_OUTER = 0.95f;
    private static final int GLOW_ALPHA = 110;
    private static final float SHELL = 0.46f;
    private static final float SEAM = 0.06f;
    private static final int SHELL_ALPHA = 120;
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/white.png");
    private static final float EDGE_OUT = 0.3f;
    private static final float EDGE_THICK = 0.45f;

    private final ModelPart model;
    private final ModelPart edges;
    private final ModelPart shell;

    public FloatingBallRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.3f;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("bottom", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-4, -4, -4, 8, 4, 8)
                .texOffs(32, 20).addBox(-4, -4, -4, 8, 4, 8, new CubeDeformation(0.25f)), PartPose.ZERO);
        root.addOrReplaceChild("lid", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4, -8, -4, 8, 4, 8)
                .texOffs(0, 0).addBox(-1, -5, -4.34f, 2, 2, 0)
                .texOffs(0, 20).addBox(-4, -8, -4, 8, 4, 8, new CubeDeformation(0.26f)), PartPose.ZERO);
        model = LayerDefinition.create(mesh, 64, 32).bakeRoot();

        float s = EDGE_OUT, y0 = -8 - s, y1 = s, t = EDGE_THICK, h = t / 2;
        CubeListBuilder lines = CubeListBuilder.create().texOffs(0, 0);
        CubeListBuilder band = CubeListBuilder.create().texOffs(0, 0);
        for (float a : new float[]{-4 - s, 4 + s}) {
            for (float y : new float[]{y0, y1}) {
                lines.addBox(-4 - s, y - h, a - h, 8 + 2 * s, t, t);
                lines.addBox(a - h, y - h, -4 - s, t, t, 8 + 2 * s);
            }
            for (float b : new float[]{-4 - s, 4 + s}) lines.addBox(a - h, y0, b - h, t, y1 - y0, t);
        }
        band.addBox(-1.4f, -5.4f, -4.5f, 2.8f, 0.6f, 0.4f);
        band.addBox(-1.4f, -3.2f, -4.5f, 2.8f, 0.6f, 0.4f);
        band.addBox(-1.4f, -5.4f, -4.5f, 0.6f, 2.8f, 0.4f);
        band.addBox(0.8f, -5.4f, -4.5f, 0.6f, 2.8f, 0.4f);
        MeshDefinition edgeMesh = new MeshDefinition();
        edgeMesh.getRoot().addOrReplaceChild("edges", lines, PartPose.ZERO);
        edgeMesh.getRoot().addOrReplaceChild("band", band, PartPose.ZERO);
        edges = LayerDefinition.create(edgeMesh, 16, 16).bakeRoot();

        MeshDefinition shellMesh = new MeshDefinition();
        shellMesh.getRoot().addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4 - SHELL, -8 - SHELL, -4 - SHELL, 8 + 2 * SHELL, 4 + SHELL - SEAM, 8 + 2 * SHELL), PartPose.ZERO);
        shellMesh.getRoot().addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4 - SHELL, -4 + SEAM, -4 - SHELL, 8 + 2 * SHELL, 4 + SHELL - SEAM, 8 + 2 * SHELL), PartPose.ZERO);
        shell = LayerDefinition.create(shellMesh, 16, 16).bakeRoot();
    }

    @Override
    public void render(FloatingBall ball, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float time = ball.tickCount + partialTick;
        float bob = Mth.sin(time * 0.08f) * 0.08f;
        float size = ball.size(), grow = size / FloatingBall.SIZE, scale = size / MODEL_SIZE;
        boolean golden = ball.golden();

        pose.pushPose();
        pose.translate(0, size / 2 + bob, 0);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(ball.getYRot() + time * 1.5f));
        pose.mulPose(Axis.XP.rotationDegrees(TILT));
        pose.translate(0, -size / 2, 0);
        pose.scale(scale, -scale, -scale);
        float hue = time * 0.006f;
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(golden ? GOLDEN_TEXTURE : TEXTURE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        VertexConsumer wash = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        shell.getChild("lid").render(pose, wash, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, SHELL_ALPHA << 24 | (golden
                ? Mth.hsvToRgb(0.14f + Mth.sin(hue) * 0.02f, 0.35f, 1f)
                : Mth.hsvToRgb((0.93f + Mth.sin(hue) * 0.06f + 1f) % 1f, 0.35f, 1f)));
        shell.getChild("bottom").render(pose, wash, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, SHELL_ALPHA << 24 | (golden
                ? Mth.hsvToRgb(0.11f + Mth.sin(hue * 1.3f) * 0.02f, 0.4f, 1f)
                : Mth.hsvToRgb(0.5f + Mth.sin(hue * 1.3f) * 0.08f, 0.3f, 1f)));
        VertexConsumer glowing = buffers.getBuffer(RenderType.eyes(WHITE));
        edges.getChild("edges").render(pose, glowing, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFF000000 | (golden
                ? Mth.hsvToRgb(0.13f, 0.3f, 1f)
                : Mth.hsvToRgb((time * 0.01f) % 1f, 0.3f, 0.8f)));
        edges.getChild("band").render(pose, glowing, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.scale(grow, grow, grow);
        glow(pose.last().pose(), buffers.getBuffer(RenderType.debugQuads()), time, golden);
        pose.popPose();
        if (QuickBattleTargeting.target == ball.getId()) {
            target(pose, buffers, entityRenderDispatcher.cameraOrientation(), (float) Math.sqrt(entityRenderDispatcher.distanceToSqr(ball)), RADIUS * grow, time);
        }
        pose.popPose();
        super.render(ball, yaw, partialTick, pose, buffers, light);
    }

    public static void target(PoseStack pose, MultiBufferSource buffers, Quaternionf camera, float distance, float radius, float time) {
        pose.pushPose();
        pose.mulPose(camera);
        float pixel = PIXEL * Math.max(1f, distance / 6f);
        float pulse = (int) (time / 20) % 2 == 0 ? 0 : PULSE * pixel;
        brackets(pose.last().pose(), buffers.getBuffer(RenderType.debugQuads()), radius + pulse, pixel);
        pose.popPose();
    }

    private static void brackets(Matrix4f m, VertexConsumer vc, float radius, float pixel) {
        int cells = Mth.ceil((radius + pixel) / pixel);
        for (int i = -cells; i <= cells; i++) {
            for (int j = -cells; j <= cells; j++) {
                float x = (i + 0.5f) * pixel, y = (j + 0.5f) * pixel;
                float r = Mth.sqrt(x * x + y * y);
                if (r < radius || r >= radius + pixel) continue;
                if (Math.abs(Mth.atan2(y, Math.abs(x))) > ARC) continue;
                square(m, vc, i * pixel, j * pixel, pixel);
            }
        }
    }

    private static void glow(Matrix4f m, VertexConsumer vc, float time, boolean golden) {
        float a1 = 0;
        int c1 = glowColour(a1, time, golden);
        for (int s = 0; s < GLOW_STEPS; s++) {
            float a0 = a1;
            int c0 = c1;
            a1 = (s + 1) * Mth.TWO_PI / GLOW_STEPS;
            c1 = glowColour(a1, time, golden);
            glowVertex(m, vc, a0, GLOW_INNER, c0, GLOW_ALPHA);
            glowVertex(m, vc, a0, GLOW_OUTER, c0, 0);
            glowVertex(m, vc, a1, GLOW_OUTER, c1, 0);
            glowVertex(m, vc, a1, GLOW_INNER, c1, GLOW_ALPHA);
        }
    }

    private static int glowColour(float angle, float time, boolean golden) {
        return golden ? Mth.hsvToRgb(0.12f + Mth.sin(angle * 2 + time * 0.05f) * 0.03f, 0.55f, 1f) : Mth.hsvToRgb((angle / Mth.TWO_PI + time * 0.01f) % 1f, 0.35f, 1f);
    }

    private static void glowVertex(Matrix4f m, VertexConsumer vc, float angle, float r, int rgb, int alpha) {
        vc.addVertex(m, Mth.cos(angle) * r, Mth.sin(angle) * r, -0.45f).setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha);
    }

    private static void square(Matrix4f m, VertexConsumer vc, float x, float y, float size) {
        vc.addVertex(m, x, y, 0.05f).setColor(255, 255, 255, 230);
        vc.addVertex(m, x + size, y, 0.05f).setColor(255, 255, 255, 230);
        vc.addVertex(m, x + size, y + size, 0.05f).setColor(255, 255, 255, 230);
        vc.addVertex(m, x, y + size, 0.05f).setColor(255, 255, 255, 230);
    }

    @Override
    public boolean shouldRender(FloatingBall ball, Frustum frustum, double x, double y, double z) {
        return ball.belongsTo(Minecraft.getInstance().player) && super.shouldRender(ball, frustum, x, y, z);
    }

    @Override
    public ResourceLocation getTextureLocation(FloatingBall ball) {
        return ball.golden() ? GOLDEN_TEXTURE : TEXTURE;
    }
}
