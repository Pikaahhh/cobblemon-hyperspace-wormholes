package dev.hyperspacewormholes.hyperspace;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * The Hyperspace sky, which is just a slowly spinning textured sphere around the camera.
 */
public class HyperspaceSky {
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Hyperspace.MOD_ID, "textures/environment/hyperspace_sky.png");
    private static final int COLUMNS = 48;
    private static final int ROWS = 24;
    private static final float RADIUS = 100f;

    public static float angle(float ticks) {
        return (float) Math.toRadians(ticks * 0.004f);
    }

    public static void register() {
        DimensionRenderingRegistry.registerSkyRenderer(Hyperspace.HYPERSPACE, HyperspaceSky::render);
    }

    private static void render(WorldRenderContext context) {
        float time = context.world().getGameTime() + context.tickCounter().getGameTimeDeltaPartialTick(false);
        Matrix4f matrix = new Matrix4f(context.positionMatrix()).rotateY(angle(time));

        Matrix4fStack view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.set(matrix);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEXTURE);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int column = 0; column < COLUMNS; column++) {
            float u0 = column / (float) COLUMNS, u1 = (column + 1) / (float) COLUMNS;
            for (int row = 0; row < ROWS; row++) {
                float v0 = row / (float) ROWS, v1 = (row + 1) / (float) ROWS;
                vertex(buffer, u0, v0);
                vertex(buffer, u0, v1);
                vertex(buffer, u1, v1);
                vertex(buffer, u1, v0);
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        view.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static void vertex(BufferBuilder buffer, float u, float v) {
        double azimuth = u * Math.PI * 2;
        double elevation = Math.PI / 2 - v * Math.PI;
        float x = (float) (Math.cos(elevation) * Math.cos(azimuth)) * RADIUS;
        float y = (float) Math.sin(elevation) * RADIUS;
        float z = (float) (Math.cos(elevation) * Math.sin(azimuth)) * RADIUS;
        buffer.addVertex(x, y, z).setUv(u, v);
    }
}
