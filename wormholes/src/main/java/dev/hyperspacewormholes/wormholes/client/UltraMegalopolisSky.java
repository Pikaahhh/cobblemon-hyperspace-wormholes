package dev.hyperspacewormholes.wormholes.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * The Ultra Megalopolis's sky, drawn in place of vanilla's: space on a flat near-black navy, vanilla-style stars plus a
 * Milky Way band of dimmer ones, and five small pixel-art planets, one per Ultra Beast world. Time never moves there,
 * so the meshes are built once and kept on the graphics card (VertexBuffer).
 */
public final class UltraMegalopolisSky {
    private static final float RADIUS = 100;
    private static final int STARS = 1500, BAND_STARS = 1400, SEGMENTS = 16;
    private static final float[] SPACE = {0.020f, 0.024f, 0.058f};
    private static final float BAND_TILT = 62f, BAND_SPREAD = 0.11f, PLANET_SIZE = 4.8f;
    private static @Nullable VertexBuffer space;
    private static final VertexBuffer[] PLANET_MESHES = new VertexBuffer[5];
    private static final Planet[] PLANETS = {
            new Planet("planet_crater", 40, 26),
            new Planet("planet_jungle", 165, 30),
            new Planet("planet_desert", 245, 47),
            new Planet("planet_plant", 100, 58),
            new Planet("planet_deep_sea", 312, -12),
    };
    private static final float[][] STAR_QUADS = stars();

    private record Planet(ResourceLocation texture, Vector3f dir) {
        Planet(String name, float bearing, float elevation) {
            this(Wormholes.id("textures/environment/" + name + ".png"), direction(bearing, elevation));
        }
    }

    private UltraMegalopolisSky() {}

    public static void render(WorldRenderContext context) {
        if (space == null) build();
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(context.positionMatrix());
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        space.bind();
        space.drawWithShader(view, RenderSystem.getProjectionMatrix(), GameRenderer.getPositionColorShader());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int i = 0; i < PLANETS.length; i++) {
            RenderSystem.setShaderTexture(0, PLANETS[i].texture());
            PLANET_MESHES[i].bind();
            PLANET_MESHES[i].drawWithShader(view, RenderSystem.getProjectionMatrix(), GameRenderer.getPositionTexColorShader());
        }
        VertexBuffer.unbind();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void build() {
        Matrix4f pose = new Matrix4f();
        BufferBuilder sky = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        fill(sky, pose);
        space = upload(sky);
        for (int i = 0; i < PLANETS.length; i++) {
            BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            planet(quad, pose, PLANETS[i]);
            PLANET_MESHES[i] = upload(quad);
        }
    }

    private static VertexBuffer upload(BufferBuilder builder) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(builder.buildOrThrow());
        VertexBuffer.unbind();
        return buffer;
    }

    private static void fill(VertexConsumer space, Matrix4f pose) {
        for (int ring = 0; ring < SEGMENTS; ring++) {
            float a0 = -Mth.HALF_PI + ring * Mth.PI / SEGMENTS, a1 = -Mth.HALF_PI + (ring + 1) * Mth.PI / SEGMENTS;
            for (int s = 0; s < SEGMENTS * 2; s++) {
                float b0 = s * Mth.TWO_PI / (SEGMENTS * 2), b1 = (s + 1) * Mth.TWO_PI / (SEGMENTS * 2);
                sphere(space, pose, a0, b0);
                sphere(space, pose, a0, b1);
                sphere(space, pose, a1, b1);
                sphere(space, pose, a1, b0);
            }
        }
        for (float[] q : STAR_QUADS) {
            for (int v = 0; v < 4; v++) {
                space.addVertex(pose, q[v * 3], q[v * 3 + 1], q[v * 3 + 2]).setColor(q[12], q[13], q[14], 1f);
            }
        }
    }

    private static void planet(VertexConsumer quad, Matrix4f pose, Planet planet) {
        Vector3f d = planet.dir();
        Vector3f right = new Vector3f(0, 1, 0).cross(d).normalize().mul(PLANET_SIZE);
        Vector3f up = new Vector3f(d).cross(right).normalize().mul(PLANET_SIZE);
        Vector3f c = new Vector3f(d).mul(RADIUS);
        quad.addVertex(pose, c.x - right.x - up.x, c.y - right.y - up.y, c.z - right.z - up.z).setUv(0, 1).setColor(1f, 1f, 1f, 1f);
        quad.addVertex(pose, c.x + right.x - up.x, c.y + right.y - up.y, c.z + right.z - up.z).setUv(1, 1).setColor(1f, 1f, 1f, 1f);
        quad.addVertex(pose, c.x + right.x + up.x, c.y + right.y + up.y, c.z + right.z + up.z).setUv(1, 0).setColor(1f, 1f, 1f, 1f);
        quad.addVertex(pose, c.x - right.x + up.x, c.y - right.y + up.y, c.z - right.z + up.z).setUv(0, 0).setColor(1f, 1f, 1f, 1f);
    }

    private static void sphere(VertexConsumer builder, Matrix4f pose, float elevation, float bearing) {
        float flat = Mth.cos(elevation) * RADIUS * 1.2f;
        builder.addVertex(pose, flat * Mth.cos(bearing), Mth.sin(elevation) * RADIUS * 1.2f, flat * Mth.sin(bearing))
                .setColor(SPACE[0], SPACE[1], SPACE[2], 1f);
    }

    private static Vector3f direction(float bearing, float elevation) {
        float b = bearing * Mth.DEG_TO_RAD, e = elevation * Mth.DEG_TO_RAD;
        return new Vector3f(Mth.cos(e) * Mth.cos(b), Mth.sin(e), Mth.cos(e) * Mth.sin(b));
    }

    private static float[][] stars() {
        Random random = new Random(10_800L);
        float[][] quads = new float[STARS + BAND_STARS][];
        Vector3f bandNormal = direction(20, 90 - BAND_TILT);
        for (int i = 0; i < quads.length; i++) {
            boolean band = i >= STARS;
            Vector3f d;
            if (!band) {
                d = new Vector3f(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1);
                if (d.lengthSquared() > 1 || d.lengthSquared() < 0.01f) {
                    i--;
                    continue;
                }
                d.normalize();
            } else {
                Vector3f a = new Vector3f(0, 1, 0).cross(bandNormal).normalize();
                Vector3f b = new Vector3f(bandNormal).cross(a).normalize();
                float t = random.nextFloat() * Mth.TWO_PI;
                float off = (float) random.nextGaussian() * BAND_SPREAD;
                d = new Vector3f(a).mul(Mth.cos(t)).add(new Vector3f(b).mul(Mth.sin(t))).add(new Vector3f(bandNormal).mul(off)).normalize();
            }
            float size = band ? 0.10f + random.nextFloat() * 0.08f : 0.15f + random.nextFloat() * 0.1f;
            float bright = band ? 0.30f + random.nextFloat() * 0.35f : 0.55f + random.nextFloat() * 0.45f;
            float r = bright, g = bright, bl = bright;
            float tint = random.nextFloat();
            if (tint < 0.10f) {
                r *= 0.78f;
                g *= 0.88f;
            } else if (tint < 0.16f) {
                bl *= 0.72f;
                g *= 0.92f;
            }
            Vector3f c = new Vector3f(d).mul(RADIUS);
            Vector3f ref = Math.abs(d.y) > 0.95f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
            Vector3f u = new Vector3f(ref).cross(d).normalize();
            Vector3f v = new Vector3f(d).cross(u).normalize();
            float spin = random.nextFloat() * Mth.TWO_PI;
            Vector3f x = new Vector3f(u).mul(Mth.cos(spin)).add(new Vector3f(v).mul(Mth.sin(spin))).mul(size);
            Vector3f y = new Vector3f(v).mul(Mth.cos(spin)).sub(new Vector3f(u).mul(Mth.sin(spin))).mul(size);
            float[] q = new float[15];
            int k = 0;
            for (int[] corner : new int[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) {
                q[k++] = c.x + x.x * corner[0] + y.x * corner[1];
                q[k++] = c.y + x.y * corner[0] + y.y * corner[1];
                q[k++] = c.z + x.z * corner[0] + y.z * corner[1];
            }
            q[12] = r;
            q[13] = g;
            q[14] = bl;
            quads[i] = q;
        }
        return quads;
    }
}
