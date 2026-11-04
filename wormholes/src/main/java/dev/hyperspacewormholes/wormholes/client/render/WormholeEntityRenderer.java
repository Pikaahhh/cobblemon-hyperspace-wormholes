package dev.hyperspacewormholes.wormholes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.wormholes.client.ShaderPacks;
import dev.hyperspacewormholes.wormholes.entity.WormholeEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Draws wormholes in the world: the funnel opening and closing. By water (or seen from under water) it is drawn with the
 * entities, so a water surface in front of it tints it, except when the camera and the whole wormhole are under water (the
 * Ultra Deep Sea), where the translucent blocks behind it (the Luminescent Ice) would otherwise paint over its glow. Never
 * culled, so Entity Culling can't drop a way home whose box is mostly in rock.
 */
public class WormholeEntityRenderer extends EntityRenderer<WormholeEntity> {
    private static final List<Consumer<MultiBufferSource>> AFTER_WATER = new ArrayList<>();

    public WormholeEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public void render(WormholeEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        WarpTextures.ensureReady();
        float time = entity.tickCount + partialTick;
        float open = Math.min(1f, time / 20f);
        open = 1 - (1 - open) * (1 - open) * (1 - open);
        if (entity.getLifetime() > 0) {
            float left = entity.getLifetime() - time;
            if (left <= 0) return;
            open = Math.min(open, left / WormholeEntity.CLOSE_TICKS);
        }

        float yawRad = entity.getYRot() * Mth.DEG_TO_RAD, pitch = entity.getXRot() * Mth.DEG_TO_RAD;
        float flat = Mth.cos(pitch);
        Quaternionf facing = new Quaternionf().rotationTo(0, 0, 1,
                -Mth.sin(yawRad) * flat, -Mth.sin(pitch), Mth.cos(yawRad) * flat);

        Matrix4f pose = new Matrix4f(poseStack.last().pose()).translate(0, WormholeEntity.SIZE / 2, 0);
        Quaternionf camera = new Quaternionf(entityRenderDispatcher.cameraOrientation());
        float radius = WormholeEntity.RADIUS * entity.getScale() * open, alpha = open;
        int rings = entity.getRings();
        var type = entity.getWormholeType();
        boolean rainbow = entity.isRainbow();
        Consumer<MultiBufferSource> draw = target -> WormholeVisual.draw(target, pose, facing, camera, time, radius, rings,
                type, rainbow, alpha);
        boolean cameraInWater = Minecraft.getInstance().gameRenderer.getMainCamera().getFluidInCamera() == FogType.WATER;
        if ((entity.isNearWater() || cameraInWater) && !(cameraInWater && entity.isSubmerged())) {
            draw.accept(buffers);
        } else {
            AFTER_WATER.add(draw);
        }
    }

    public static void clearQueued() {
        AFTER_WATER.clear();
    }

    public static void drawAfterWater(WorldRenderContext context) {
        if (ShaderPacks.inUse()) flush();
    }

    public static void drawLast(WorldRenderContext context) {
        if (!ShaderPacks.inUse() && !AFTER_WATER.isEmpty()) ShaderPacks.inCameraView(context, WormholeEntityRenderer::flush);
    }

    private static void flush() {
        if (AFTER_WATER.isEmpty()) return;
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        AFTER_WATER.forEach(draw -> draw.accept(buffers));
        AFTER_WATER.clear();
        buffers.endBatch();
    }

    @Override
    public ResourceLocation getTextureLocation(WormholeEntity entity) {
        return WarpTextures.FUNNEL;
    }
}
