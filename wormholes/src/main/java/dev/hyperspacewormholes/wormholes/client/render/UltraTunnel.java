package dev.hyperspacewormholes.wormholes.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hyperspacewormholes.wormholes.client.RideClient;
import dev.hyperspacewormholes.wormholes.ride.WarpTunnel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.Arrays;

/**
 * Ultra Space's tunnel during the ride, drawn in TunnelRenderer's sky pass: walls of navy smoke, clouds and stars wound
 * round the way ahead, with patches of violet, teal and the legend's own colour, dark towards a black middle so
 * wormholes, rings and the legend stand out; motes hanging in it, and streaks that rush past, longer and brighter in a
 * boost. The walls are drawn far end first, as the sky pass has no depth.
 */
final class UltraTunnel {
    interface Batch {
        VertexConsumer begin(ResourceLocation texture, boolean additive);

        void end(VertexConsumer consumer);
    }

    private interface Shade {
        void at(float round, float z, float[] out);
    }

    private static final float Z_NEAR = -50f;
    private static final int SIDES = 36;
    private static final int STEPS = 22;
    private static final float[] COS = new float[SIDES + 1], SIN = new float[SIDES + 1];
    private static final float[] MIDDLE = {0.008f, 0.008f, 0.026f};
    private static final float[][] PATCH = {{0.42f, 0.10f, 0.40f}, {0.50f, 0.16f, 0.22f}, {0.46f, 0.08f, 0.44f},
            {0.42f, 0.30f, 0.30f}};
    private static final float MOTE_SPACING = 4.5f;
    /** The colours of its clouds and smoke, for the cloud round the mouth of the opening's corridor. */
    static final float[][] CLOUDS = {{0.09f, 0.13f, 0.44f}, {0.26f, 0.26f, 0.78f}};

    static {
        for (int i = 0; i <= SIDES; i++) {
            COS[i] = Mth.cos(i * Mth.TWO_PI / SIDES);
            SIN[i] = Mth.sin(i * Mth.TWO_PI / SIDES);
        }
    }

    private final Batch batch;
    private final Matrix4f pose;
    private final float time, pt, length, heldBack, offX, offY, boost;
    private final double progress;
    private final float[] patch;

    private UltraTunnel(Batch batch, Matrix4f pose, float time, float pt, float length, float heldBack) {
        this.batch = batch;
        this.pose = pose;
        this.time = time;
        this.pt = pt;
        this.length = length;
        this.heldBack = heldBack;
        progress = RideClient.progress(pt);
        offX = RideClient.offX(pt);
        offY = RideClient.offY(pt);
        patch = PATCH[RideClient.mount().ordinal()];
        boost = Mth.clamp((RideClient.speed(pt) - WarpTunnel.CRUISE_SPEED) / (WarpTunnel.BOOST_SPEED - WarpTunnel.CRUISE_SPEED),
                0, 1);
    }

    static void draw(Batch batch, Matrix4f atRide, float time, float pt, float length, float heldBack) {
        UltraTunnel tunnel = new UltraTunnel(batch, atRide, time, pt, length, heldBack);
        tunnel.middle();
        tunnel.walls();
        tunnel.motes();
        tunnel.streaks();
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    private static float wrap(float v) {
        return v - Mth.floor(v);
    }

    private float nearFade(float z) {
        return Mth.clamp(1 + (z + heldBack) / 40f, 0f, 1f);
    }

    private float lit(float z) {
        return 0.05f + 0.95f * (1 - smoothstep(0.35f, 1f, z / length));
    }

    private float taper(float z) {
        float t = Math.max(0, z / length);
        return 1 - 0.5f * t * t;
    }

    private void middle() {
        VertexConsumer vc = batch.begin(UltraTunnelTextures.PLAIN, false);
        square(vc, WarpTunnel.bendX(length, time) - offX, WarpTunnel.bendY(length, time) - offY, length, 40, MIDDLE[0], MIDDLE[1],
                MIDDLE[2], 1);
        batch.end(vc);
    }

    private void walls() {
        wall(UltraTunnelTextures.SMOKE, false, 56, 3, 120, 0.6f, 0.011f, 1f / 1100, (round, z, c) -> {
            float lit = lit(z), p = patches(round, z, 0);
            set(c, Mth.lerp(p, CLOUDS[1][0], patch[0] * 1.6f) * lit, Mth.lerp(p, CLOUDS[1][1], patch[1] * 1.6f) * lit,
                    Mth.lerp(p, CLOUDS[1][2], patch[2] * 1.8f) * lit, nearFade(z));
        });
        wall(WarpTextures.CLOUD, false, 44, 4, 90, 0.8f, -0.005f, 1f / 480, (round, z, c) -> {
            float lit = lit(z), p = patches(round, z, 1.7f), teal = patches(round, z, 4.4f) * (1 - p);
            set(c, Mth.lerp(teal, Mth.lerp(p, CLOUDS[0][0], patch[0] * 1.3f), 0.07f) * lit,
                    Mth.lerp(teal, Mth.lerp(p, CLOUDS[0][1], patch[1] * 1.3f), 0.38f) * lit,
                    Mth.lerp(teal, Mth.lerp(p, CLOUDS[0][2], patch[2] * 1.4f), 0.52f) * lit, 0.8f * nearFade(z));
        });
        wall(WarpTextures.CLOUD, true, 40, 3, 70, 1f, 0.006f, -1f / 620, (round, z, c) -> {
            float p = patches(round, z, 1.7f), pulse = boost * (0.5f + 0.5f * Mth.sin(z * 0.06f + time * 0.4f));
            set(c, Mth.lerp(p, 0.16f, patch[0] * 1.6f), Mth.lerp(p, 0.26f, patch[1] * 1.6f), Mth.lerp(p, 0.75f, patch[2] * 1.6f),
                    (0.2f + 0.16f * p + 0.3f * pulse) * nearFade(z) * lit(z) * smoothstep(-10, 60, z));
        });
        wall(UltraTunnelTextures.ARCS, true, 38, 4, 80, 0.9f, 0.006f, 1f / 700, (round, z, c) ->
                set(c, 0.28f, 0.42f, 0.85f, (0.09f + 0.2f * boost) * nearFade(z) * lit(z) * smoothstep(0, 80, z)));
        wall(WarpTextures.STARFIELD, true, 36, 6, 50, 1.3f, 0.002f, 1f / 800, (round, z, c) ->
                set(c, 0.8f, 0.88f, 1f, 0.5f * nearFade(z) * (1 - smoothstep(90, 240, z))));
    }

    private void wall(ResourceLocation texture, boolean additive, float radius, int repeat, float vScale, float scroll, float twist,
                      float spin, Shade shade) {
        VertexConsumer vc = batch.begin(texture, additive);
        float turn = wrap(time * spin), from = Z_NEAR - heldBack;
        float scrolled = wrap((float) (progress * scroll / vScale % 1.0));
        float[] near = new float[(SIDES + 1) * 4], far = new float[(SIDES + 1) * 4], c = new float[4];
        float z1 = length;
        shadeRow(shade, z1, turn, far, c);
        for (int k = STEPS - 1; k >= 0; k--) {
            float z0 = from + (length - from) * (float) Math.pow(k / (float) STEPS, 1.5);
            shadeRow(shade, z0, turn, near, c);
            float x0 = WarpTunnel.bendX(z0, time) - offX, y0 = WarpTunnel.bendY(z0, time) - offY, r0 = radius * taper(z0);
            float x1 = WarpTunnel.bendX(z1, time) - offX, y1 = WarpTunnel.bendY(z1, time) - offY, r1 = radius * taper(z1);
            float v0 = z0 / vScale + scrolled, v1 = z1 / vScale + scrolled;
            float u0 = turn * repeat + z0 * twist, u1 = turn * repeat + z1 * twist;
            for (int s = 0; s < SIDES; s++) {
                float f0 = s * repeat / (float) SIDES, f1 = (s + 1) * repeat / (float) SIDES;
                int i = s * 4;
                vc.addVertex(pose, x0 + COS[s] * r0, y0 + SIN[s] * r0, z0).setUv(f0 + u0, v0)
                        .setColor(near[i], near[i + 1], near[i + 2], near[i + 3]);
                vc.addVertex(pose, x0 + COS[s + 1] * r0, y0 + SIN[s + 1] * r0, z0).setUv(f1 + u0, v0)
                        .setColor(near[i + 4], near[i + 5], near[i + 6], near[i + 7]);
                vc.addVertex(pose, x1 + COS[s + 1] * r1, y1 + SIN[s + 1] * r1, z1).setUv(f1 + u1, v1)
                        .setColor(far[i + 4], far[i + 5], far[i + 6], far[i + 7]);
                vc.addVertex(pose, x1 + COS[s] * r1, y1 + SIN[s] * r1, z1).setUv(f0 + u1, v1)
                        .setColor(far[i], far[i + 1], far[i + 2], far[i + 3]);
            }
            float[] swap = near;
            near = far;
            far = swap;
            z1 = z0;
        }
        batch.end(vc);
    }

    private static void shadeRow(Shade shade, float z, float turn, float[] row, float[] c) {
        for (int s = 0; s <= SIDES; s++) {
            Arrays.fill(c, 1);
            shade.at((s % SIDES) / (float) SIDES + turn, z, c);
            for (int k = 0; k < 4; k++) row[s * 4 + k] = Mth.clamp(c[k], 0, 1);
        }
    }

    private static void set(float[] out, float r, float g, float b, float a) {
        out[0] = r;
        out[1] = g;
        out[2] = b;
        out[3] = a;
    }

    private float patches(float round, float z, float seed) {
        float p = (float) ((z + progress) % 100_000.0);
        float a = round * Mth.TWO_PI;
        float wave = Mth.sin(a + p * 0.011f + seed) + 0.8f * Mth.sin(2 * a - p * 0.017f + seed * 2.3f) + 0.6f * Mth.sin(3 * a + p * 0.029f + seed * 0.7f);
        return smoothstep(0.6f, 1.9f, wave);
    }

    private void motes() {
        VertexConsumer vc = batch.begin(WarpTextures.STAR, true);
        int first = Mth.floor((progress + Z_NEAR - heldBack) / MOTE_SPACING);
        int last = Mth.floor((progress + 190) / MOTE_SPACING);
        for (int i = first; i <= last; i++) {
            long h = mix(RideClient.seed() ^ i * 0x9E3779B97F4A7C15L);
            float z = (float) (i * MOTE_SPACING + part(h, 0) * MOTE_SPACING - progress);
            float angle = part(h, 1) * Mth.TWO_PI, r = 10 + 22 * Mth.sqrt(part(h, 2)), half = (0.14f + 0.18f * part(h, 3)) * 2.2f;
            float a = nearFade(z) * (1 - smoothstep(90, 185, z)) * (0.45f + 0.4f * part(h, 4));
            if (a < 0.02f) continue;
            float x = WarpTunnel.bendX(z, time) - offX + Mth.cos(angle + time * 0.002f) * r;
            float y = WarpTunnel.bendY(z, time) - offY + Mth.sin(angle + time * 0.002f) * r;
            square(vc, x, y, z, half, 0.35f, 0.85f, 0.95f, a);
        }
        batch.end(vc);
    }

    private void square(VertexConsumer vc, float x, float y, float z, float half, float r, float g, float b, float a) {
        vc.addVertex(pose, x - half, y - half, z).setUv(0, 1).setColor(r, g, b, a);
        vc.addVertex(pose, x + half, y - half, z).setUv(1, 1).setColor(r, g, b, a);
        vc.addVertex(pose, x + half, y + half, z).setUv(1, 0).setColor(r, g, b, a);
        vc.addVertex(pose, x - half, y + half, z).setUv(0, 0).setColor(r, g, b, a);
    }

    private void streaks() {
        VertexConsumer vc = batch.begin(UltraTunnelTextures.PLAIN, true);
        float stretch = RideClient.speed(pt) / WarpTunnel.CRUISE_SPEED, strength = 0.42f + 0.58f * boost;
        for (RideClient.Streak streak : RideClient.streaks()) {
            float z = Mth.lerp(RideClient.travelled(pt), streak.prevZ, streak.z);
            if (z > length) continue;
            float z1 = z + streak.length * 0.28f * stretch * (0.6f + 1.6f * boost);
            float a = strength * streak.brightness * smoothstep(-6, 18, z) * (1 - smoothstep(0.3f, 0.75f, z / length));
            if (a < 0.02f) continue;
            float cos = Mth.cos(streak.angle), sin = Mth.sin(streak.angle), half = streak.width * (0.42f + 0.22f * boost);
            float x0 = WarpTunnel.bendX(z, time) - offX + cos * streak.radius, y0 = WarpTunnel.bendY(z, time) - offY + sin * streak.radius;
            float x1 = WarpTunnel.bendX(z1, time) - offX + cos * streak.radius, y1 = WarpTunnel.bendY(z1, time) - offY + sin * streak.radius;
            float wx = -sin * half, wy = cos * half;
            vc.addVertex(pose, x0 - wx, y0 - wy, z).setUv(0, 0).setColor(0.7f, 0.85f, 1f, a);
            vc.addVertex(pose, x0 + wx, y0 + wy, z).setUv(1, 0).setColor(0.7f, 0.85f, 1f, a);
            vc.addVertex(pose, x1 + wx, y1 + wy, z1).setUv(1, 1).setColor(0.5f, 0.7f, 1f, a * 0.35f);
            vc.addVertex(pose, x1 - wx, y1 - wy, z1).setUv(0, 1).setColor(0.5f, 0.7f, 1f, a * 0.35f);
        }
        batch.end(vc);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float part(long h, int slot) {
        return (mix(h + slot * 0xD1B54A32D192ED03L) >>> 40) / (float) (1 << 24);
    }
}
