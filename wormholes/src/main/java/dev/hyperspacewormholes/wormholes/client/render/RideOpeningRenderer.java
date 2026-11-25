package dev.hyperspacewormholes.wormholes.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hyperspacewormholes.wormholes.client.RideOpening;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Draws the corridor of light of the ride's opening shot (RideOpening) over Ultra Space. Its wall is a round cone of
 * SIDES sides, four to each of the tunnel's, with pixels of flat colour lying on it (WarpTextures.WALL, and RUSH_WALL
 * once the corridor stretches): COLUMNS round it, so the pixels on both sides of the corridor's mouth look the same
 * size. They run from violet at the mouth through blue and cyan and back (wall). Over that are bands and lines of light
 * on the same grid, sparkles and rays. Everything on the wall is placed by how far it is between the corridor's mouth
 * (0) and its near end (1), so when the near end is stretched back the rows stretch into rays.
 * <p>
 * The mouth is a real hole onto the tunnel behind it: past the wall's end the corridor goes on as a tail of pixel cloud
 * in the tunnel's own colours that thins out to nothing, a step at a time. The mouth widens past the edges of the
 * picture as the camera flies in.
 * <p>
 * It is all geometry in the world, drawn behind the legend in the sky pass, in game units round the middle of the ride,
 * shrunk towards the camera where it would reach past the far plane.
 */
public final class RideOpeningRenderer {
    public static final int SOLID = 0;
    public static final int WALL = 1;
    public static final int BANDS = 2;
    public static final int CURTAINS = 3;
    public static final int SPARKLES = 4;
    public static final int SMOKE = 5;
    public static final int RUSH_WALL = 6;

    public interface Batch {
        VertexConsumer begin(int texture, boolean additive);

        void end(VertexConsumer consumer);
    }

    private static final int SIDES = 144;
    /** Pixels of the wall round it, and rows of them from its mouth to its end. */
    public static final int COLUMNS = 192;
    public static final int ROWS = 192;
    private static final int HALVES = COLUMNS * 2;
    private static final float[] SIDE_COS = new float[SIDES + 1];
    private static final float[] SIDE_SIN = new float[SIDES + 1];
    private static final float[] HALF_COS = new float[HALVES + 1];
    private static final float[] HALF_SIN = new float[HALVES + 1];
    /** How much narrower a stretch of the wall drawn in one piece may get, so its pixels stay square to it. */
    private static final float TAPER = 1.16f;
    /** How far behind the camera the wall starts, in its radius there, so wide screens never see past its end. */
    private static final float BEHIND = 6f;
    private static final int LINES = 24;
    private static final int BAND_ROWS = 20;
    private static final float BAND_DRIFT = 0.0015f;
    private static final float RUSH_SPEED = -0.0000045f;
    private static final int RAYS = 150;
    private static final float[] LINE_LIGHT = new float[LINES];
    private static final int[] RAY_COLUMN = new int[RAYS];
    private static final int[] RAY_WIDTH = new int[RAYS];
    private static final float[] RAY_OUT = new float[RAYS];
    private static final float[] RAY_AT = new float[RAYS];
    private static final float[] RAY_LENGTH = new float[RAYS];
    private static final float[] RAY_LIGHT = new float[RAYS];
    private static final float[][] RAY_COLOUR = new float[RAYS][];
    private static final int STREAKS = 46;
    private static final float STREAK_SPAN = 9000f;
    private static final float STREAK_SPEED = 190f;
    private static final float STREAK_WIDTH = 12f;
    private static final float FASTER = 330f;
    private static final float FASTER_OVER = 40f;
    private static final float[] STREAK_ANGLE = new float[STREAKS];
    private static final float[] STREAK_OUT = new float[STREAKS];
    private static final float[] STREAK_AT = new float[STREAKS];
    private static final float[] STREAK_LENGTH = new float[STREAKS];
    private static final float[][] STREAK_COLOUR = new float[STREAKS][];
    /** How far back along the wall, then its colour there. */
    private static final float[][] WALL_COLOURS = {
            {0f, 0.60f, 0.42f, 0.94f}, {0.045f, 0.60f, 0.42f, 0.94f}, {0.065f, 0.55f, 0.46f, 0.94f},
            {0.09f, 0.44f, 0.56f, 0.92f}, {0.16f, 0.44f, 0.60f, 0.91f}, {0.22f, 0.45f, 0.60f, 0.91f},
            {0.27f, 0.40f, 0.67f, 0.91f}, {0.36f, 0.41f, 0.69f, 0.89f}, {0.46f, 0.38f, 0.71f, 0.79f},
            {0.57f, 0.39f, 0.78f, 0.75f}, {0.66f, 0.38f, 0.75f, 0.73f}, {0.71f, 0.39f, 0.74f, 0.74f},
            {0.76f, 0.41f, 0.66f, 0.83f}, {0.82f, 0.43f, 0.57f, 0.88f}, {0.90f, 0.47f, 0.52f, 0.89f},
            {0.95f, 0.55f, 0.46f, 0.93f}, {1f, 0.60f, 0.42f, 0.94f},
    };
    private static final float[][] LIGHTS = {
            {0.25f, 1f, 0.45f}, {1f, 0.35f, 0.5f}, {0.35f, 0.95f, 1f}, {1f, 0.45f, 0.95f}, {0.4f, 0.5f, 1f},
    };
    private static final float[] WHITE = {1f, 1f, 1f};
    private static final float[] WALL_LIGHT = {0.94f, 0.94f, 0.94f};
    private static final float[] PALE = {0.92f, 0.94f, 1f};
    private static final float[] PINK = {1f, 0.90f, 1f};
    private static final float[] INDIGO = {0.10f, 0.08f, 0.26f};
    private static final float[] BLACK = {0.01f, 0.01f, 0.03f};
    /** The tail past the mouth: how far on each step of it starts, its radius there and how solid its cloud is. */
    private static final float[][] TAIL = {
            {0f, 781.5f, 0.95f}, {380f, 765f, 0.95f}, {760f, 743f, 0.9f}, {1150f, 692f, 0.7f}, {1534f, 640f, 0.45f},
            {2010f, 535f, 0.25f}, {2491f, 430f, 0f},
    };
    private static final float TAIL_END = 2491f;
    private static final float[] TAIL_LIGHT = {0.44f, 0.49f, 0.66f};

    static {
        for (int i = 0; i <= SIDES; i++) {
            SIDE_COS[i] = Mth.cos(i * Mth.TWO_PI / SIDES);
            SIDE_SIN[i] = Mth.sin(i * Mth.TWO_PI / SIDES);
        }
        for (int i = 0; i <= HALVES; i++) {
            HALF_COS[i] = Mth.cos(i * Mth.TWO_PI / HALVES);
            HALF_SIN[i] = Mth.sin(i * Mth.TWO_PI / HALVES);
        }
        Random random = new Random(4417);
        for (int i = 0; i < LINES; i++) LINE_LIGHT[i] = 0.35f + random.nextInt(4) * 0.2f;
        Random widths = new Random(91);
        for (int i = 0; i < RAYS; i++) {
            RAY_COLUMN[i] = random.nextInt(HALVES);
            RAY_WIDTH[i] = i % 9 == 0 ? 4 + widths.nextInt(4) : 1 + widths.nextInt(2);
            RAY_OUT[i] = random.nextFloat() < 0.6f ? 0.985f : 0.6f + random.nextFloat() * 0.35f;
            RAY_AT[i] = 0.0012f * (float) Math.pow(400, random.nextFloat());
            RAY_LENGTH[i] = RAY_AT[i] * (0.4f + random.nextFloat() * 1.6f);
            RAY_LIGHT[i] = 0.12f + random.nextInt(4) * 0.11f;
            if (i % 9 == 0) RAY_LIGHT[i] = 0.15f;
            RAY_COLOUR[i] = random.nextFloat() < 0.5f ? (random.nextBoolean() ? PALE : PINK)
                    : random.nextFloat() < 0.6f ? pale(LIGHTS[2], 0.15f) : pale(LIGHTS[random.nextInt(LIGHTS.length)], 0.2f);
        }
        for (int i = 0; i < STREAKS; i++) {
            STREAK_ANGLE[i] = random.nextFloat() * Mth.TWO_PI;
            STREAK_OUT[i] = 110f + random.nextFloat() * 320f;
            STREAK_AT[i] = random.nextFloat();
            STREAK_LENGTH[i] = 120f + random.nextFloat() * 260f;
            STREAK_COLOUR[i] = LIGHTS[random.nextInt(LIGHTS.length)];
        }
    }

    private RideOpeningRenderer() {}

    /**
     * Draws the corridor on this game frame. view is the view matrix with its origin at the camera, camX, camY and camZ
     * where the camera is from the middle of the ride, in blocks, and tunnel the colours of the tunnel's clouds.
     */
    public static void render(Batch batch, Matrix4f view, float camX, float camY, float camZ, float unit, float depthFar,
                              float frame, float[][] tunnel) {
        float mouth = RideOpening.mouth(frame), size = RideOpening.scale(frame);
        float eye = camZ / unit, reach = mouth + TAIL_END + 5000f - eye;
        if (mouth + TAIL_END <= eye) return;
        float shrink = Math.min(1f, depthFar * 0.8f / (reach * unit));
        Matrix4f pose = new Matrix4f(view).scale(unit * shrink).translate(-camX / unit, -camY / unit, -eye);
        tail(batch, pose, frame, mouth, size, tunnel);
        if (mouth <= eye) return;
        wall(batch, pose, frame, mouth, size, eye);
        streaks(batch, pose, frame, mouth, eye);
    }

    private static void wall(Batch batch, Matrix4f pose, float frame, float mouth, float size, float eye) {
        float end = RideOpening.nearEnd(frame) - mouth;
        float slope = (RideOpening.NEAR_RADIUS - RideOpening.MOUTH_RADIUS) * size;
        float mouthRadius = RideOpening.MOUTH_RADIUS * size;
        float eyeRadius = mouthRadius + slope * (eye - mouth) / end;
        float behind = eye - BEHIND * eyeRadius, ahead = mouth - eye;
        float rays = RideOpening.smooth(92f, 124f, frame);
        float near = RideOpening.smooth(160f, 187f, frame);
        Cone cone = new Cone(pose, mouth, end, mouthRadius, slope);

        float stretched = RideOpening.smooth(84f, 100f, frame);
        VertexConsumer vc;
        for (int pass = stretched < 1 ? 0 : 1; pass < (stretched > 0 ? 2 : 1); pass++) {
            vc = batch.begin(pass == 0 ? WALL : RUSH_WALL, false);
            cone.pixels(vc, behind, mouth, 1f, 0, 0f, 1f, WALL_LIGHT, pass == 0 ? 1f : stretched);
            batch.end(vc);
        }

        float bands = 1f - RideOpening.smooth(112f, 136f, frame);
        if (bands > 0) {
            vc = batch.begin(BANDS, true);
            float wraps = (float) ROWS / BAND_ROWS;
            cone.rings(vc, behind, mouth, 0.985f, -frame * BAND_DRIFT, wraps, PALE, 0.34f * bands);
            batch.end(vc);
        }

        vc = batch.begin(CURTAINS, true);
        float from = behind;
        for (int i = 0; i < 3 && from < mouth; i++) {
            float to = Math.min(mouth, eye + eyeRadius * (3f + 4f * i)), light = Mth.lerp(rays, 0.4f, 0.1f) * (1 - 0.33f * i);
            if (light < 0.07f) break;
            cone.pixels(vc, from, to, 0.99f, 0, snap(frame * 0.0008f), ROWS / 64f, PALE, light);
            from = to;
        }
        batch.end(vc);

        float glitter = 0.6f * (1 - rays);
        if (glitter > 0) {
            vc = batch.begin(SPARKLES, true);
            float middle = Math.min(mouth, eye + 2f * eyeRadius), far = Math.min(mouth, eye + 5f * eyeRadius);
            cone.pixels(vc, behind, middle, 0.9f, 0, snap(frame * 0.0012f), 1f, WHITE, glitter);
            cone.pixels(vc, middle, far, 0.9f, 0, snap(frame * 0.0012f), 1f, WHITE, glitter * 0.5f);
            batch.end(vc);
        }

        float rush = rays * (1 - RideOpening.smooth(176f, 190f, frame));
        if (rush > 0) {
            vc = batch.begin(SPARKLES, true);
            cone.pixels(vc, behind, mouth - 0.1f * ahead, 0.97f, 0, RUSH_SPEED * faster(frame), 1f, WHITE, 0.45f * rush);
            batch.end(vc);
        }

        vc = batch.begin(SOLID, false);
        for (int i = 0; i < 3; i++) {
            float back = i == 0 ? 1.4f : 0.95f - 0.45f * (i - 1);
            cone.ring(vc, mouth - back * mouthRadius, mouth - (i == 2 ? 0f : 0.95f - 0.45f * i) * mouthRadius, 1f, INDIGO,
                    0.22f * near * (i + 1) / 3f);
        }
        batch.end(vc);

        vc = batch.begin(SOLID, true);
        float back = Math.max(behind, mouth + end * 1.4f), backS = (back - mouth) / end;
        for (int i = 0; i < LINES; i++) {
            int half = i * COLUMNS / LINES * 2;
            float light = Mth.lerp(rays, 0.36f, 0.6f * LINE_LIGHT[i]);
            float middle = mouth - 0.45f * ahead, thin = Mth.lerp(rays, 1f, 0.4f);
            cone.strip(vc, half, back, middle, 0.985f, PALE, light);
            cone.strip(vc, half, middle, mouth, 0.985f, PALE, light * thin);
            cone.strip(vc, half + 1, back, middle, 0.985f, PALE, light * Mth.lerp(rays, 0.4f, 0.25f));
            cone.strip(vc, half + 1, middle, mouth, 0.985f, PALE, light * Mth.lerp(rays, 0.4f, 0.25f) * thin);
        }
        float webs = RideOpening.smooth(94f, 106f, frame) * (1 - RideOpening.smooth(128f, 142f, frame));
        for (int i = 0; i < 4; i++) {
            float s = 0.014f + i * 0.026f + (i % 2) * 0.006f;
            cone.ring(vc, mouth + end * (s + 0.0015f), mouth + end * s, 0.985f, PALE, 0.22f * webs);
        }
        if (rays > 0) {
            for (int i = 0; i < RAYS; i++) {
                float s0 = RAY_AT[i], s1 = s0 + RAY_LENGTH[i];
                if (s0 > backS) continue;
                s1 = Math.min(s1, backS);
                float z0 = mouth + end * s0, z1 = mouth + end * s1, dim = Mth.lerp(0.4f, z0, z1);
                float a = rays * RAY_LIGHT[i] * Mth.clamp((z1 - eye) / (eyeRadius * 2f) + 1f, 0.3f, 1f);
                for (int half = RAY_COLUMN[i]; half < RAY_COLUMN[i] + RAY_WIDTH[i]; half++) {
                    cone.strip(vc, half % HALVES, z1, dim, RAY_OUT[i], RAY_COLOUR[i], a);
                    cone.strip(vc, half % HALVES, dim, z0, RAY_OUT[i], RAY_COLOUR[i], a * 0.6f);
                }
                if (RAY_OUT[i] < 0.95f) {
                    cone.fin(vc, RAY_COLUMN[i], z1, dim, RAY_OUT[i], RAY_COLOUR[i], a);
                    cone.fin(vc, RAY_COLUMN[i], dim, z0, RAY_OUT[i], RAY_COLOUR[i], a * 0.6f);
                }
            }
        }
        float first = RideOpening.smooth(122f, 128f, frame) * (1 - RideOpening.smooth(142f, 150f, frame));
        float at = 0.071f + (frame - 132f) * 0.0008f;
        cone.ring(vc, mouth + end * (at + 0.0035f), mouth + end * at, 0.985f, PALE, 0.22f * first);
        float second = RideOpening.smooth(160f, 167f, frame);
        cone.ring(vc, mouth + end * 0.0204f, mouth + end * 0.0185f, 0.985f, PINK, 0.3f * second);
        float glow = Mth.lerp(near, 0.1f, 0.2f);
        for (int i = 0; i < 4; i++) {
            cone.ring(vc, mouth - (1f - i * 0.25f) * mouthRadius, mouth - (0.75f - i * 0.25f) * mouthRadius, 0.985f, PINK,
                    glow * (i + 1) / 4f);
        }
        cone.ring(vc, mouth - 0.1f * mouthRadius, mouth - 0.04f * mouthRadius, 0.985f, WHITE, 0.25f * near);
        cone.ring(vc, mouth - 0.04f * mouthRadius, mouth + 0.06f * mouthRadius, 0.985f, WHITE, 0.45f * near);
        batch.end(vc);
    }

    /** The corridor's cloudy end: blocky cloud thinning out from the mouth, dark inside while it is far away. */
    private static void tail(Batch batch, Matrix4f pose, float frame, float mouth, float size, float[][] tunnel) {
        float open = RideOpening.smooth(RideOpening.HOLE_OPENS, 185f, frame), near = RideOpening.smooth(160f, 187f, frame);
        float lit = RideOpening.smooth(166f, 188f, frame);
        int steps = TAIL.length - 1;
        float[][] colour = new float[steps][3];
        for (int i = 0; i < steps; i++) {
            float[] cloud = tunnel[i % 2 == 0 ? 1 : 0];
            for (int k = 0; k < 3; k++) {
                colour[i][k] = Mth.lerp(0.35f, Mth.lerp(lit, 0.26f, TAIL_LIGHT[k]), cloud[k]) * (1.3f - 0.14f * i);
            }
        }
        if (open < 1) {
            VertexConsumer vc = batch.begin(SOLID, false);
            disc(vc, pose, mouth + TAIL_END, TAIL[steps][1] * size, BLACK, 1 - open);
            for (int i = steps - 1; i >= 0; i--) {
                float[] deep = i > 2 ? BLACK : new float[]{colour[i][0] * 0.5f, colour[i][1] * 0.5f, colour[i][2] * 0.5f};
                step(pose, i, mouth, size).ring(vc, mouth + TAIL[i + 1][0], mouth + TAIL[i][0], 1f, deep, 1 - open);
            }
            batch.end(vc);
        }
        VertexConsumer vc = batch.begin(SMOKE, false);
        for (int layer = 0; layer < 2; layer++) {
            int turn = layer == 0 ? Mth.floor(frame * 0.12f) : COLUMNS - 23 - Mth.floor(frame * 0.18f);
            for (int i = layer == 0 ? steps - 1 : 3; i >= 0; i--) {
                step(pose, i, mouth, size).pixels(vc, mouth + TAIL[i + 1][0], mouth + TAIL[i][0], layer == 0 ? 1f : 0.94f, turn,
                        i + layer * 2.5f, 0.19f, colour[i], TAIL[i][2] * (i == 0 ? near : 1f));
            }
        }
        batch.end(vc);
    }

    /** One step of the tail as a cone of its own, from where it starts (0) to where it ends (1). */
    private static Cone step(Matrix4f pose, int i, float mouth, float size) {
        return new Cone(pose, mouth + TAIL[i][0], TAIL[i + 1][0] - TAIL[i][0], TAIL[i][1] * size,
                (TAIL[i + 1][1] - TAIL[i][1]) * size);
    }

    /** Coloured bars of light coming down the corridor, gone before its mouth. */
    private static void streaks(Batch batch, Matrix4f pose, float frame, float mouth, float eye) {
        float shown = RideOpening.smooth(30f, 60f, frame) * (1 - RideOpening.smooth(172f, 184f, frame));
        if (shown <= 0) return;
        float fast = RideOpening.smooth(84f, 118f, frame);
        VertexConsumer vc = batch.begin(SOLID, true);
        for (int i = 0; i < STREAKS * 2; i++) {
            int k = i % STREAKS;
            float there = i < STREAKS ? 1f : fast, travel = frame * STREAK_SPEED + faster(frame);
            float run = (STREAK_AT[k] + (i < STREAKS ? 0f : 0.37f)) * STREAK_SPAN
                    - travel * (0.8f + 0.4f * STREAK_AT[(k * 7) % STREAKS]);
            float z = eye + run - Mth.floor(run / STREAK_SPAN) * STREAK_SPAN;
            float length = STREAK_LENGTH[k] * Mth.lerp(fast, 1f, 3.2f);
            float a = shown * there * Mth.clamp((z - eye) / Mth.lerp(fast, 500f, 1500f), 0f, 1f)
                    * Mth.clamp((eye + STREAK_SPAN - z) / 2500f, 0f, 1f) * Mth.clamp((mouth - z - length) / 1500f, 0f, 1f);
            if (a < 0.01f) continue;
            float out = STREAK_OUT[k] * Mth.lerp(fast, 1f, 2.1f), wide = STREAK_WIDTH * Mth.lerp(fast, 1f, 0.6f);
            float turn = STREAK_ANGLE[k] + (i < STREAKS ? 0f : 2.4f);
            float x = Mth.cos(turn) * out, y = Mth.sin(turn) * out;
            for (int part = 0; part < 3; part++) {
                float z0 = z + length * part / 3f, z1 = z + length * (part + 1) / 3f, light = a * (0.7f - part * 0.23f);
                bar(vc, pose, x, y, z0, z1, wide, 0f, STREAK_COLOUR[k], light);
                bar(vc, pose, x, y, z0, z1, 0f, wide, STREAK_COLOUR[k], light);
            }
        }
        batch.end(vc);
    }

    /** How much further than at their first speed the lights have run by this frame, once the corridor stretches. */
    private static float faster(float frame) {
        float x = Math.max(0f, frame - 84f);
        return x < FASTER_OVER ? FASTER * x * x / (2 * FASTER_OVER) : FASTER * (x - FASTER_OVER / 2);
    }

    private static void bar(VertexConsumer vc, Matrix4f pose, float x, float y, float z0, float z1, float wx, float wy,
                            float[] rgb, float a) {
        vertex(vc, pose, x - wx, y - wy, z0, 0.5f, 0.5f, rgb, a);
        vertex(vc, pose, x + wx, y + wy, z0, 0.5f, 0.5f, rgb, a);
        vertex(vc, pose, x + wx, y + wy, z1, 0.5f, 0.5f, rgb, a);
        vertex(vc, pose, x - wx, y - wy, z1, 0.5f, 0.5f, rgb, a);
    }

    private static void disc(VertexConsumer vc, Matrix4f pose, float z, float r, float[] rgb, float a) {
        for (int i = 0; i < SIDES; i += 2) {
            vertex(vc, pose, 0, 0, z, 0.5f, 0.5f, rgb, a);
            for (int k = 0; k <= 2; k++) vertex(vc, pose, SIDE_COS[i + k] * r, SIDE_SIN[i + k] * r, z, 0.5f, 0.5f, rgb, a);
        }
    }

    /** A stretch of the round cone: where its mouth is, how far on its end is, and its radius and how much that grows. */
    private record Cone(Matrix4f pose, float mouth, float end, float mouthRadius, float slope) {
        float radius(float z) {
            return mouthRadius + slope * (z - mouth) / end;
        }

        /**
         * The wall from z0 to z1 at this share of the radius, with a texture once round it, turned by this many pixels, and
         * vWraps times along the cone.
         */
        void pixels(VertexConsumer vc, float z0, float z1, float share, int turn, float vShift, float vWraps, float[] rgb,
                    float a) {
            if (a <= 0.004f || z0 == z1) return;
            float first = radius(z0), last = radius(z1);
            int pieces = Math.max(1, Mth.ceil(Math.abs(Math.log(first / last)) / Math.log(TAPER)));
            float from = z0;
            for (int i = 1; i <= pieces; i++) {
                float r = first * (float) Math.pow(last / first, (float) i / pieces);
                float to = i == pieces ? z1 : z0 + (z1 - z0) * (r - first) / (last - first);
                piece(vc, from, to, share, 1f, (float) turn / COLUMNS, row(from, vShift, vWraps), row(to, vShift, vWraps), rgb, a);
                from = to;
            }
        }

        private float row(float z, float vShift, float vWraps) {
            return ((z - mouth) / end + vShift) * vWraps;
        }

        private void piece(VertexConsumer vc, float z0, float z1, float share, float uWraps, float uShift, float v0, float v1,
                           float[] rgb, float a) {
            float r0 = radius(z0) * share, r1 = radius(z1) * share;
            for (int i = 0; i < SIDES; i++) {
                float u0 = uWraps * i / SIDES + uShift, u1 = uWraps * (i + 1) / SIDES + uShift;
                vertex(vc, pose, SIDE_COS[i] * r0, SIDE_SIN[i] * r0, z0, u0, v0, rgb, a);
                vertex(vc, pose, SIDE_COS[i + 1] * r0, SIDE_SIN[i + 1] * r0, z0, u1, v0, rgb, a);
                vertex(vc, pose, SIDE_COS[i + 1] * r1, SIDE_SIN[i + 1] * r1, z1, u1, v1, rgb, a);
                vertex(vc, pose, SIDE_COS[i] * r1, SIDE_SIN[i] * r1, z1, u0, v1, rgb, a);
            }
        }

        /** A texture that is the same all the way round, vWraps times along the cone. */
        void rings(VertexConsumer vc, float z0, float z1, float share, float vShift, float vWraps, float[] rgb, float a) {
            piece(vc, z0, z1, share, 0f, 0.5f, row(z0, vShift, vWraps), row(z1, vShift, vWraps), rgb, a);
        }

        /** One flat colour all the way round, from z0 to z1. */
        void ring(VertexConsumer vc, float z0, float z1, float share, float[] rgb, float a) {
            if (a > 0.004f) piece(vc, z0, z1, share, 0f, 0.5f, 0.5f, 0.5f, rgb, a);
        }

        /** Half a column of pixels from z0 to z1, in one flat colour. */
        void strip(VertexConsumer vc, int half, float z0, float z1, float share, float[] rgb, float a) {
            if (a <= 0.004f) return;
            float r0 = radius(z0) * share, r1 = radius(z1) * share;
            vertex(vc, pose, HALF_COS[half] * r0, HALF_SIN[half] * r0, z0, 0.5f, 0.5f, rgb, a);
            vertex(vc, pose, HALF_COS[half + 1] * r0, HALF_SIN[half + 1] * r0, z0, 0.5f, 0.5f, rgb, a);
            vertex(vc, pose, HALF_COS[half + 1] * r1, HALF_SIN[half + 1] * r1, z1, 0.5f, 0.5f, rgb, a);
            vertex(vc, pose, HALF_COS[half] * r1, HALF_SIN[half] * r1, z1, 0.5f, 0.5f, rgb, a);
        }

        /** A strip on its edge, standing in from the middle of the flat one, so the two make a bar. */
        void fin(VertexConsumer vc, int half, float z0, float z1, float share, float[] rgb, float a) {
            float x = (HALF_COS[half] + HALF_COS[half + 1]) / 2, y = (HALF_SIN[half] + HALF_SIN[half + 1]) / 2;
            float in = share - 2f / COLUMNS, r0 = radius(z0), r1 = radius(z1);
            vertex(vc, pose, x * r0 * share, y * r0 * share, z0, 0.5f, 0.5f, rgb, a);
            vertex(vc, pose, x * r0 * in, y * r0 * in, z0, 0.5f, 0.5f, rgb, a);
            vertex(vc, pose, x * r1 * in, y * r1 * in, z1, 0.5f, 0.5f, rgb, a);
            vertex(vc, pose, x * r1 * share, y * r1 * share, z1, 0.5f, 0.5f, rgb, a);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, float x, float y, float z, float u, float v, float[] rgb,
                               float alpha) {
        vc.addVertex(pose, x, y, z).setUv(u, v).setColor(rgb[0], rgb[1], rgb[2], alpha);
    }

    private static float snap(float s) {
        return Mth.floor(s * ROWS) / (float) ROWS;
    }

    /** The wall's colour this far back from its mouth. */
    static float[] wall(float s) {
        float x = s - Mth.floor(s);
        int i = 1;
        while (x > WALL_COLOURS[i][0]) i++;
        float t = (x - WALL_COLOURS[i - 1][0]) / (WALL_COLOURS[i][0] - WALL_COLOURS[i - 1][0]);
        float[] out = new float[3];
        for (int k = 0; k < 3; k++) out[k] = Mth.lerp(t, WALL_COLOURS[i - 1][k + 1], WALL_COLOURS[i][k + 1]) * 0.97f;
        return out;
    }

    private static float[] pale(float[] rgb, float by) {
        return new float[]{Mth.lerp(by, rgb[0], 1f), Mth.lerp(by, rgb[1], 1f), Mth.lerp(by, rgb[2], 1f)};
    }
}
