package dev.hyperspacewormholes.wormholes.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hyperspacewormholes.wormholes.ride.WormholeType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a single wormhole: a glowing pixel-art grid funnel into a bright throat, coloured by type, with rarity rings.
 */
public final class WormholeVisual {
    private static final float[] RING_COLOR = {1.00f, 0.78f, 0.25f};
    private static final int PROFILE = 16;
    private static final int SPECKS = 8;
    private static final int BANDS = 4;

    public static void draw(MultiBufferSource buffers, Matrix4f pose, Quaternionf facing, Quaternionf cameraRotation,
                            float time, float radius, int rings, WormholeType type, boolean rainbow, float alpha) {
        float[] c = rainbow ? rainbow(time, 0) : new float[]{type.r, type.g, type.b};
        float[] bright = {Mth.lerp(0.45f, c[0], 1), Mth.lerp(0.45f, c[1], 1), Mth.lerp(0.45f, c[2], 1)};
        Matrix4f m = new Matrix4f(pose).rotate(facing);
        Funnel funnel = new Funnel(radius);

        VertexConsumer glow = buffers.getBuffer(WarpRenderTypes.additive(WarpTextures.GLOW));
        square(glow, m, 0, 0, 0, funnel.outer * 2.6f, c[0], c[1], c[2], 0.3f * alpha);
        Matrix4f throat = new Matrix4f(m).translate(0, 0, -funnel.depth).rotate(new Quaternionf(facing).conjugate())
                .rotate(cameraRotation);
        square(glow, throat, 0, 0, 0, funnel.throat * 7f, c[0], c[1], c[2], alpha);
        square(glow, throat, 0, 0, 0, funnel.throat * 3.5f, bright[0], bright[1], bright[2], alpha);

        drawFunnel(buffers.getBuffer(WarpRenderTypes.additive(WarpTextures.FUNNEL)), m, funnel,
                Math.floorMod(Mth.floor(time / 3), WarpTextures.FUNNEL_FRAMES), c, rainbow ? time : Float.NaN, alpha);

        VertexConsumer star = buffers.getBuffer(WarpRenderTypes.additive(WarpTextures.STAR));
        Vector3f p = new Vector3f();
        float step = Mth.floor(time / 2) * 2;
        for (int i = 0; i < SPECKS; i++) {
            float phase = (step * 0.012f + i / (float) SPECKS) % 1f;
            funnel.point(phase * (PROFILE - 1), i * Mth.TWO_PI / SPECKS + phase * 5f, p);
            float a = alpha * Math.min(1f, phase * 8f) * (1 - phase * 0.6f);
            square(star, m, p.x, p.y, p.z, radius * 0.22f, bright[0], bright[1], bright[2], a);
        }

        for (int i = 0; i < rings; i++) {
            ring(buffers, m, funnel.outer * 0.06f * (i + 1), funnel.outer * (1.05f + 0.16f * i), WarpTextures.THIN_RING,
                    WarpTextures.THIN_RING_GLOW, alpha);
        }
    }

    public static void goldRing(MultiBufferSource buffers, Matrix4f pose, float radius, float alpha) {
        ring(buffers, pose, 0, radius, WarpTextures.RING, WarpTextures.RING_GLOW, alpha);
    }

    private static void ring(MultiBufferSource buffers, Matrix4f pose, float z, float radius, ResourceLocation ring,
                             ResourceLocation halo, float alpha) {
        float size = radius * 2 / WarpTextures.RING_RADIUS;
        square(buffers.getBuffer(WarpRenderTypes.additive(halo)), pose, 0, 0, z, size,
                RING_COLOR[0], RING_COLOR[1] * 0.85f, RING_COLOR[2] * 0.6f, 0.75f * alpha);
        square(buffers.getBuffer(WarpRenderTypes.additive(ring)), pose, 0, 0, z, size,
                RING_COLOR[0], RING_COLOR[1], RING_COLOR[2], alpha);
    }

    private static final class Funnel {
        private static final float CONE = 0.5f;
        final float outer, throat, depth;

        Funnel(float radius) {
            outer = radius * 1.7f;
            throat = outer * WarpTextures.FUNNEL_THROAT;
            depth = radius * 1.8f;
        }

        float radiusAt(float j) {
            return outer * (float) Math.pow(throat / outer, j / (PROFILE - 1));
        }

        float depthAt(float r) {
            float cone = (outer - r) / (outer - throat);
            float well = (1 / r - 1 / outer) / (1 / throat - 1 / outer);
            return depth * (CONE * cone + (1 - CONE) * well);
        }

        void point(float j, float angle, Vector3f out) {
            float r = radiusAt(j);
            out.set(Mth.cos(angle) * r, Mth.sin(angle) * r, -depthAt(r));
        }
    }

    private static float[] rainbow(float time, float offset) {
        int rgb = Mth.hsvToRgb((time * 0.01f + offset) % 1f, 0.75f, 1f);
        return new float[]{(rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f};
    }

    private static void drawFunnel(VertexConsumer vc, Matrix4f m, Funnel funnel, int frame, float[] c, float rainbowTime,
                                   float alpha) {
        float frameV = (float) frame / WarpTextures.FUNNEL_FRAMES;
        float frameH = 1f / WarpTextures.FUNNEL_FRAMES;
        float[] col = new float[3];
        int segments = WarpTextures.FUNNEL_SEGMENTS;
        for (int j = 0; j < PROFILE - 1; j++) {
            float r0 = funnel.radiusAt(j), r1 = funnel.radiusAt(j + 1);
            float z0 = -funnel.depthAt(r0), z1 = -funnel.depthAt(r1);
            int band = Math.min(BANDS - 1, j * BANDS / (PROFILE - 1));
            if (!Float.isNaN(rainbowTime)) c = rainbow(rainbowTime, j / (float) (PROFILE - 1));
            float shade = 0.55f + 0.45f * band / (BANDS - 1);
            float white = band == BANDS - 1 ? 0.35f : 0f;
            for (int k = 0; k < 3; k++) col[k] = Mth.lerp(white, c[k] * shade, 1f);
            float a = alpha * (j == 0 ? 0.35f : j == 1 ? 0.7f : 1f);
            for (int s = 0; s < segments; s++) {
                float t0 = s * Mth.TWO_PI / segments, t1 = (s + 1) * Mth.TWO_PI / segments;
                corner(vc, m, funnel, Mth.cos(t0) * r0, Mth.sin(t0) * r0, z0, frameV, frameH, col, a);
                corner(vc, m, funnel, Mth.cos(t1) * r0, Mth.sin(t1) * r0, z0, frameV, frameH, col, a);
                corner(vc, m, funnel, Mth.cos(t1) * r1, Mth.sin(t1) * r1, z1, frameV, frameH, col, a);
                corner(vc, m, funnel, Mth.cos(t0) * r1, Mth.sin(t0) * r1, z1, frameV, frameH, col, a);
            }
        }
    }

    private static void corner(VertexConsumer vc, Matrix4f m, Funnel funnel, float x, float y, float z,
                               float frameV, float frameH, float[] col, float a) {
        float u = 0.5f + x / (2 * funnel.outer);
        float v = frameV + (0.5f - y / (2 * funnel.outer)) * frameH;
        vc.addVertex(m, x, y, z).setUv(u, v).setColor(col[0], col[1], col[2], a);
    }

    static void square(VertexConsumer vc, Matrix4f pose, float x, float y, float z, float size,
                       float r, float g, float b, float a) {
        float h = size / 2;
        vc.addVertex(pose, x - h, y - h, z).setUv(0, 1).setColor(r, g, b, a);
        vc.addVertex(pose, x + h, y - h, z).setUv(1, 1).setColor(r, g, b, a);
        vc.addVertex(pose, x + h, y + h, z).setUv(1, 0).setColor(r, g, b, a);
        vc.addVertex(pose, x - h, y + h, z).setUv(0, 0).setColor(r, g, b, a);
    }
}
