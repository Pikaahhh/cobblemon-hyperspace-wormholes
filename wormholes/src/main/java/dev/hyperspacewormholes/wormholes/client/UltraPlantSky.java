package dev.hyperspacewormholes.wormholes.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The Ultra Plant's sky, drawn in place of vanilla's: a stormy navy, darkest overhead and paling to blue down to the
 * horizon where it meets the biome's fog colour, with no sun, moon, stars or clouds. A lightning flash lights the sky
 * above the horizon for its two ticks.
 */
public final class UltraPlantSky {
    private static final float RADIUS = 100, FLASH_MIX = 0.35f;
    private static final int SEGMENTS = 32;
    private static final float[] ELEVATIONS = {-90, 0, 10, 35, 90};
    private static final float[][] COLOURS = {null, null, rgb(0x22486B), rgb(0x173253), rgb(0x0E223E)};
    private static final float[] FLASH = rgb(0x7090B8);

    private UltraPlantSky() {}

    public static void render(WorldRenderContext context) {
        float[] fog = RenderSystem.getShaderFogColor();
        float flash = context.world().getSkyFlashTime() > 0 ? FLASH_MIX : 0;
        Matrix4f pose = context.positionMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int ring = 0; ring < ELEVATIONS.length - 1; ring++) {
            float[] low = colour(ring, fog, flash), high = colour(ring + 1, fog, flash);
            float a0 = ELEVATIONS[ring] * Mth.DEG_TO_RAD, a1 = ELEVATIONS[ring + 1] * Mth.DEG_TO_RAD;
            for (int s = 0; s < SEGMENTS; s++) {
                float b0 = s * Mth.TWO_PI / SEGMENTS, b1 = (s + 1) * Mth.TWO_PI / SEGMENTS;
                vertex(builder, pose, a0, b0, low);
                vertex(builder, pose, a0, b1, low);
                vertex(builder, pose, a1, b1, high);
                vertex(builder, pose, a1, b0, high);
            }
        }
        MeshData mesh = builder.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static float[] colour(int ring, float[] fog, float flash) {
        float[] c = COLOURS[ring];
        if (c == null) return fog;
        return new float[]{Mth.lerp(flash, c[0], FLASH[0]), Mth.lerp(flash, c[1], FLASH[1]), Mth.lerp(flash, c[2], FLASH[2])};
    }

    private static void vertex(BufferBuilder builder, Matrix4f pose, float elevation, float bearing, float[] c) {
        float flat = Mth.cos(elevation) * RADIUS;
        builder.addVertex(pose, flat * Mth.cos(bearing), Mth.sin(elevation) * RADIUS, flat * Mth.sin(bearing))
                .setColor(c[0], c[1], c[2], 1f);
    }

    private static float[] rgb(int hex) {
        return new float[]{(hex >> 16 & 255) / 255f, (hex >> 8 & 255) / 255f, (hex & 255) / 255f};
    }
}
