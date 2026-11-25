package dev.hyperspacewormholes.wormholes.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hyperspacewormholes.wormholes.client.SmoothCurve;
import dev.hyperspacewormholes.wormholes.client.render.WarpCloseTextures.Sprite;
import dev.hyperspacewormholes.wormholes.ride.RideLegend;
import dev.hyperspacewormholes.wormholes.ride.WormholeType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * What's drawn round a wormhole as it closes, as in Ultra Sun and Ultra Moon: arms of light spiral in, a white core
 * bursts into a ball with two shells and jagged spikes, there's a flash, and out of it comes a white spiral inside a
 * widening ring, with rays, that tightens into a point of light and goes out. Sizes and brightnesses follow the curves
 * below, in frames, 30 a second. Every part faces the camera and is added onto what's behind it, a little nearer the
 * camera than the wormhole.
 * <p>
 * In the ride (draw, WarpEntryClose counts the frames) its colours are the legend's, blue, cyan and violet-pink for
 * Lunala, gold and red for Solgaleo; the flash is the burst's ball swelling past the edges of the picture and back, the
 * point of light at the end swells into the white (WarpWhite), and it's the same size on screen whatever the field of
 * view (one unit is UNIT of half the picture's height). Out in a world (drawInWorld) it has the wormhole's own colours
 * unless a legend went in, and is sized by the wormhole; the flash only lights up its middle, and it's dimmer to
 * someone standing in it. holeScale and holeLight are how a wormhole out there shrinks and goes out.
 */
public final class WarpCloseLook {
    public static final float FRAMES_PER_TICK = 30f / 20f;
    private static final float UNIT = 0.24f, LEAST_UNIT = 1.6f, MOST_UNIT = 12f;
    private static final float[][] MOON = {{0.06f, 0.14f, 1f}, {0.62f, 0.80f, 1f}, {0.25f, 0.85f, 1f}, {0.80f, 0.45f, 1f},
            {1f, 0.10f, 0.55f}, {0.30f, 0.45f, 1f}};
    private static final float[][] SUN = {{1f, 0.26f, 0.03f}, {1f, 0.86f, 0.52f}, {1f, 0.80f, 0.20f}, {1f, 0.32f, 0.12f},
            {1f, 0.12f, 0.05f}, {1f, 0.50f, 0.10f}};
    private static final float WORLD_UNIT = 0.85f;
    private static final SmoothCurve HOLE = new SmoothCurve(0, 1, 5, 0.85f, 11, 0.5f, 14, 0.25f);
    private static final SmoothCurve HOLE_LIGHT = new SmoothCurve(11, 1, 14, 0);
    private static final int ARMS = 0, SWIRL = 1, BALL = 2, RING = 3, SPIKES = 4, SHELL = 5;
    private static final float[] WHITE = {1, 1, 1};
    /** How far each part of a wormhole's own light is from its colour towards white, or (below 0) towards a purer one. */
    private static final float[] OWN_WHITE = {-0.5f, 0.6f, 0.3f, 0.15f, -0.2f, 0.1f};
    private static final float LAST_FRAME = 52;
    private static final int DASHES = 11, SPARKS = 9;

    private static final SmoothCurve FIRST_GLOW = new SmoothCurve(0, 0, 3, 0.25f, 10, 0.6f, 12.5f, 0.8f, 15, 0);
    private static final SmoothCurve FIRST_GLOW_SIZE = new SmoothCurve(0, 0.5f, 10, 0.75f, 13, 1.4f, 15, 1.3f);
    private static final SmoothCurve SPREAD = new SmoothCurve(31, 1, 41, 0.35f);
    private static final SmoothCurve ARMS_LIGHT = new SmoothCurve(0, 0, 3, 0.1f, 9, 1, 17, 1, 22, 0.5f, 32, 0.25f, 39, 0);
    private static final SmoothCurve DASH_LIGHT = new SmoothCurve(2, 0, 7, 1, 34, 1, 39, 0);
    private static final SmoothCurve STAR = new SmoothCurve(10.5f, 0.3f, 13, 1.45f, 14.5f, 1.3f, 16, 1.2f);
    private static final SmoothCurve STAR_LIGHT = new SmoothCurve(10.5f, 0, 12.5f, 1, 13.6f, 1, 15.5f, 0);
    private static final SmoothCurve BALL_SIZE = new SmoothCurve(12.5f, 0.7f, 14, 1.05f, 15, 1.17f, 16, 1.26f, 17, 1.35f, 18.5f, 1.42f);
    private static final SmoothCurve BALL_LIGHT = new SmoothCurve(12.5f, 0, 14, 1, 17.6f, 1, 19, 0);
    private static final SmoothCurve SHELL_ONE = new SmoothCurve(14.5f, 1.2f, 15, 1.4f, 16, 2.1f, 17, 3.05f, 18, 3.8f, 19, 4.2f);
    private static final SmoothCurve SHELL_ONE_LIGHT = new SmoothCurve(14.5f, 0, 15.5f, 0.5f, 18, 0.35f, 19.5f, 0);
    private static final SmoothCurve SHELL_TWO = new SmoothCurve(15.5f, 1.2f, 16, 1.4f, 17, 2.1f, 18, 3.05f, 19, 3.6f);
    private static final SmoothCurve SHELL_TWO_LIGHT = new SmoothCurve(15.5f, 0, 16.5f, 0.6f, 18, 0.45f, 19.5f, 0);
    private static final SmoothCurve SPIKE_SIZE = new SmoothCurve(13, 0.55f, 15, 1, 16, 1.2f, 17, 1.45f, 19, 1.85f);
    private static final SmoothCurve SPIKE_LIGHT = new SmoothCurve(13, 0, 14.6f, 1, 18, 1, 20, 0);
    private static final SmoothCurve RAYS = new SmoothCurve(18, 2, 20, 2.6f, 24, 4.8f, 30, 5.4f);
    private static final SmoothCurve RAYS_LIGHT = new SmoothCurve(18, 0, 20.5f, 0.9f, 25, 0.75f, 31, 0);
    private static final SmoothCurve SWIRL_LIGHT = new SmoothCurve(17, 0, 19, 1, 31, 1, 40, 0);
    private static final SmoothCurve RING_SIZE = new SmoothCurve(18, 1.26f, 23, 1.33f, 26, 1.47f, 29, 1.65f, 32, 1.72f, 40, 1.8f);
    private static final SmoothCurve RING_LIGHT = new SmoothCurve(18, 0, 19.5f, 1, 26, 1, 29, 0.75f, 32, 0.4f, 38, 0);
    private static final SmoothCurve RING_WHITE = new SmoothCurve(22, 0.8f, 28, 0);
    private static final SmoothCurve RING_INSIDE = new SmoothCurve(22, 0.8f, 30, 0);
    private static final SmoothCurve LUMPS_LIGHT = new SmoothCurve(18, 0.8f, 25, 0.64f, 30, 0.4f, 34, 0);
    private static final SmoothCurve CORE = new SmoothCurve(11, 0.3f, 13, 0.62f, 14.5f, 0.45f, 17, 0.5f, 18, 1, 19, 1.25f, 20, 1.15f,
            21.5f, 0.8f, 23, 0.55f, 29, 0.48f, 32, 0.4f, 37, 0.33f, 40, 0.28f, 42, 0.22f, 43, 0.17f, 46, 0.03f);
    private static final SmoothCurve RIDE_CORE = new SmoothCurve(11, 0.3f, 13, 0.62f, 14.5f, 0.45f, 17, 0.5f, 18, 1, 19, 1.25f,
            20, 1.15f, 21.5f, 0.8f, 23, 0.55f, 29, 0.48f, 32, 0.4f, WarpWhite.SWELLS_FROM, 0.32f);
    /** The flash's white ball, from the burst's size (0) out past the picture's corners (1) and back in. */
    private static final SmoothCurve FLASH_OUT = new SmoothCurve(16, 0, 16.8f, 0.07f, 17.5f, 0.36f, 18.1f, 1, 18.9f, 1, 20, 0.62f,
            21.5f, 0.34f, 23, 0.18f, 26, 0.06f, 31, 0);
    private static final SmoothCurve FLASH_LIGHT = new SmoothCurve(16, 0, 16.9f, 1, 20.5f, 1, 21.5f, 0.9f, 23, 0.6f, 26, 0.26f, 31, 0);
    private static final SmoothCurve CORE_LIGHT = new SmoothCurve(11, 0, 13, 1, 43, 1, 46, 0);
    private static final SmoothCurve CORE_GLOW_SIZE = new SmoothCurve(17, 1.9f, 28, 2.8f);
    private static final SmoothCurve CORE_GLOW = new SmoothCurve(13, 0.4f, 18, 0.9f);
    private static final SmoothCurve SPARK_LIGHT = new SmoothCurve(40, 0, 43, 1, 47, 1, 51, 0);
    private static final SmoothCurve SPARK_SPREAD = new SmoothCurve(40, 0.4f, 42, 0.6f, 50, 2.4f);
    private static final SmoothCurve FLASH = new SmoothCurve(17, 0, 18, 1, 19, 0.99f, 20, 0.9f, 21, 0.78f, 22, 0.66f, 23, 0.54f,
            24, 0.41f, 25, 0.36f, 26, 0.31f, 27, 0.26f, 28, 0.22f, 29, 0.17f, 30, 0.13f, 31, 0.08f, 33, 0);

    private final VertexConsumer out;
    private final Vector3f centre = new Vector3f(), right = new Vector3f(), up = new Vector3f();
    private final float frame, unit, nearer, strength;
    private final boolean inWorld;
    private final float[][] colours;
    private final Vector3f screenRight = new Vector3f(), screenUp = new Vector3f();
    private float screenX, screenY, halfHeight;

    private WarpCloseLook(VertexConsumer out, Matrix4f pose, float size, boolean inWorld, float frame, float strength,
                          float[][] colours) {
        this.out = out;
        this.frame = frame;
        this.inWorld = inWorld;
        this.colours = colours;
        pose.transformPosition(centre.zero());
        Vector3f toCamera = new Vector3f(centre).normalize(-1);
        pose.transformDirection(up.set(0, 1, 0));
        up.cross(toCamera, right);
        if (right.lengthSquared() < 1e-5f) right.set(1, 0, 0);
        right.normalize();
        toCamera.cross(right, up);
        float away = centre.length();
        unit = inWorld ? size : Mth.clamp(UNIT * away / size, LEAST_UNIT, MOST_UNIT);
        nearer = inWorld ? Math.min(1f, 1.2f * unit / Math.max(away, 0.1f)) : 1f;
        this.strength = inWorld ? Mth.clamp((away / unit - 0.4f) / 1.4f, 0.15f, strength) : strength;
    }

    /** zoom is the projection's, one over the tangent of half the field of view. */
    public static void draw(VertexConsumer out, Matrix4f pose, Quaternionf camera, float zoom, float frame, float strength,
                            boolean sun) {
        if (frame < 0 || frame > LAST_FRAME || strength <= 0) return;
        WarpCloseLook look = new WarpCloseLook(out, pose, zoom, false, frame, strength, sun ? SUN : MOON);
        look.see(camera, zoom);
        look.draw();
    }

    /** Where in the picture the wormhole's middle is. */
    private void see(Quaternionf camera, float zoom) {
        camera.transform(screenRight.set(1, 0, 0));
        camera.transform(screenUp.set(0, 1, 0));
        float ahead = Math.max(0.1f, centre.dot(camera.transform(new Vector3f(0, 0, -1))));
        halfHeight = ahead / zoom;
        screenX = centre.dot(screenRight) / halfHeight;
        screenY = centre.dot(screenUp) / halfHeight;
        WarpWhite.at(screenX, screenY, WarpCloseTextures.PIXEL * UNIT,
                RIDE_CORE.at(WarpWhite.SWELLS_FROM) * unit / halfHeight, colours[BALL], colours[RING]);
    }

    /** For a wormhole of this radius. legend is what went into it, if one did. */
    public static void drawInWorld(VertexConsumer out, Matrix4f pose, float radius, float frame, RideLegend legend,
                                   WormholeType type, boolean rainbow, float time) {
        if (frame < 0 || frame > LAST_FRAME) return;
        float[][] colours = legend == RideLegend.SOLGALEO ? SUN : legend != RideLegend.NONE ? MOON : own(type, rainbow, time);
        new WarpCloseLook(out, pose, radius * WORLD_UNIT, true, frame, 1f, colours).draw();
    }

    public static float holeScale(float frame) {
        return HOLE.at(frame);
    }

    public static float holeLight(float frame) {
        return HOLE_LIGHT.at(frame);
    }

    /** The colours of each part for a wormhole of this type: how far towards white, and how far round the rainbow. */
    private static float[][] own(WormholeType type, boolean rainbow, float time) {
        float[][] out = new float[OWN_WHITE.length][3];
        for (int i = 0; i < out.length; i++) {
            int rgb = rainbow ? Mth.hsvToRgb((time * 0.01f + i / 6f) % 1f, 0.75f, 1f) : 0;
            float r = rainbow ? (rgb >> 16 & 0xFF) / 255f : type.r, g = rainbow ? (rgb >> 8 & 0xFF) / 255f : type.g;
            float b = rainbow ? (rgb & 0xFF) / 255f : type.b, deep = OWN_WHITE[i] < 0 ? 1 + OWN_WHITE[i] : 1;
            float white = Math.max(0, OWN_WHITE[i]), least = Math.min(r, Math.min(g, b)) * (1 - deep);
            out[i][0] = Mth.lerp(white, r - least, 1f);
            out[i][1] = Mth.lerp(white, g - least, 1f);
            out[i][2] = Mth.lerp(white, b - least, 1f);
        }
        return out;
    }

    /** How far the spirals have turned: anticlockwise, which draws their arms inwards, and faster as they tighten. */
    static float turn(float frame) {
        return Mth.DEG_TO_RAD * (2.4f * frame + 0.045f * frame * frame);
    }

    private float at(SmoothCurve curve) {
        return curve.at(frame);
    }

    private void draw() {
        float turn = turn(frame), spread = WarpCloseTextures.REACH * at(SPREAD);
        face(WarpCloseTextures.glow, at(FIRST_GLOW_SIZE), 0.805f, 0, colours[SWIRL], at(FIRST_GLOW));
        face(WarpCloseTextures.arms, spread, 0.8f, turn, colours[ARMS], at(ARMS_LIGHT));
        dashes();

        face(WarpCloseTextures.star, at(STAR), 0.745f, 0.02f * frame, WHITE, at(STAR_LIGHT));
        float ball = at(BALL_SIZE), ballLight = at(BALL_LIGHT);
        face(WarpCloseTextures.ring, ball / WarpCloseTextures.RING_AT, 0.77f, 0, colours[BALL], ballLight);
        face(WarpCloseTextures.disc, ball, 0.77f, 0, colours[BALL], ballLight * 0.3f);
        face(WarpCloseTextures.shell, at(SHELL_ONE), 0.76f, 0, colours[SHELL], at(SHELL_ONE_LIGHT));
        face(WarpCloseTextures.shell, at(SHELL_TWO), 0.76f, 0, colours[SHELL], at(SHELL_TWO_LIGHT));
        spikes();

        face(WarpCloseTextures.rays, at(RAYS), 0.765f, 0.012f * frame, colours[SWIRL], at(RAYS_LIGHT));
        face(WarpCloseTextures.swirl, spread, 0.77f, turn, colours[SWIRL], at(SWIRL_LIGHT));
        ring();

        float core = at(inWorld ? CORE : RIDE_CORE), lit = inWorld || frame < WarpWhite.SWELLS_FROM ? at(CORE_LIGHT) : 1;
        if (inWorld) face(WarpCloseTextures.disc, core, 0.75f, 0, WHITE, lit);
        else white(WarpWhite.cells(core * unit / halfHeight), 0, 0.75f, lit);
        face(WarpCloseTextures.glow, core * at(CORE_GLOW_SIZE), 0.745f, 0, colours[SWIRL], lit * at(CORE_GLOW));
        if (inWorld) sparks();
        if (inWorld) face(WarpCloseTextures.glow, 3.2f, 0.74f, 0, colours[SWIRL], at(FLASH));
        else flash();
    }

    /** The burst's white ball swelling past the edges of the picture and back. */
    private void flash() {
        float light = at(FLASH_LIGHT), out = at(FLASH_OUT);
        if (light <= 0) return;
        white(Mth.lerp(out, WarpWhite.cells(0.4f * unit / halfHeight), WarpWhite.toCorner()), Math.min(2, Math.round(out * 16)), 0.12f, light);
    }

    /** The white circle (WarpWhite), flat to the picture, with bands round it that many cells wide. */
    private void white(float radius, int bands, float near, float a) {
        if (a <= 0) return;
        float cell = WarpWhite.cell() * halfHeight, x = (WarpWhite.snappedX() - screenX) * halfHeight;
        float y = (WarpWhite.snappedY() - screenY) * halfHeight;
        Sprite s = WarpCloseTextures.dot;
        WarpWhite.circle((x0, y0, x1, y1, part) -> {
            float[] c = part == 0 ? WHITE : colours[part == 1 ? BALL : RING];
            flat(x + x0 * cell, y + y0 * cell, near, s.u0(), s.v1(), c, a);
            flat(x + x1 * cell, y + y0 * cell, near, s.u1(), s.v1(), c, a);
            flat(x + x1 * cell, y + y1 * cell, near, s.u1(), s.v0(), c, a);
            flat(x + x0 * cell, y + y1 * cell, near, s.u0(), s.v0(), c, a);
        }, radius, bands, bands);
    }

    private void flat(float x, float y, float near, float u, float v, float[] c, float a) {
        out.addVertex((centre.x + screenRight.x * x + screenUp.x * y) * near,
                (centre.y + screenRight.y * x + screenUp.y * y) * near, (centre.z + screenRight.z * x + screenUp.z * y) * near)
                .setUv(u, v).setColor(c[0], c[1], c[2], Mth.clamp(a * strength, 0, 1));
    }

    private void spikes() {
        float size = at(SPIKE_SIZE), light = at(SPIKE_LIGHT);
        if (light <= 0) return;
        int count = WarpCloseTextures.SPIKES.length;
        for (int i = 0; i < count; i++) {
            float pink = fadingOver(frame / 2.4f, i, count), white = fadingOver(frame / 2.4f + 1.5f, i, count);
            face(WarpCloseTextures.SPIKES[i], size, 0.75f, 0.03f * frame + i, colours[SPIKES], light * pink);
            face(WarpCloseTextures.SPIKES[i], size * 0.55f, 0.745f, -0.03f * frame + i, WHITE, light * white);
        }
    }

    private void ring() {
        float size = at(RING_SIZE), light = at(RING_LIGHT), white = at(RING_WHITE);
        float[] c = colours[RING], blend = {Mth.lerp(white, c[0], 1), Mth.lerp(white, c[1], 1), Mth.lerp(white, c[2], 1)};
        int count = WarpCloseTextures.LUMPS.length;
        for (int i = 0; i < count; i++) {
            face(WarpCloseTextures.LUMPS[i], size * 1.06f / WarpCloseTextures.RING_AT, 0.76f, 0.025f * frame + i, colours[RING],
                    light * at(LUMPS_LIGHT) * fadingOver(frame / 4, i, count));
        }
        face(WarpCloseTextures.ring, size / WarpCloseTextures.RING_AT, 0.755f, 0, blend, light);
        face(WarpCloseTextures.ring, size * 0.93f / WarpCloseTextures.RING_AT, 0.755f, 0, colours[BALL], light * at(RING_INSIDE));
    }

    /** How much the i-th of count pictures shows when each fades over into the next in one step of at. */
    private static float fadingOver(float at, int i, int count) {
        float off = at - i;
        off -= count * Math.round(off / count);
        float t = Math.max(0, 1 - Math.abs(off));
        return t * t * (3 - 2 * t);
    }

    private void dashes() {
        float light = at(DASH_LIGHT);
        if (light <= 0) return;
        for (int i = 0; i < DASHES; i++) {
            float at = frame / 11f + i * 0.618f, life = at - Mth.floor(at), in = life * (0.6f + 0.4f * life);
            float angle = (i * 2.399f + Mth.floor(at) * 1.7f) % Mth.TWO_PI + 0.5f * life, r = Mth.lerp(in, 4.2f, 0.9f);
            float cos = Mth.cos(angle), sin = Mth.sin(angle), lean = 0.35f;
            float dx = cos * Mth.cos(lean) - sin * Mth.sin(lean), dy = sin * Mth.cos(lean) + cos * Mth.sin(lean);
            float shown = Math.min(1, 4 * life * (1 - life));
            strip(cos * r, sin * r, dx, dy, 0.14f + 0.06f * r, 0.045f, 0.79f, colours[SHELL], 0.8f * light * shown * shown * (3 - 2 * shown));
        }
    }

    private void sparks() {
        float light = at(SPARK_LIGHT);
        if (light <= 0) return;
        for (int i = 0; i < SPARKS; i++) {
            float angle = i * 2.399f, r = (0.12f + 0.06f * (i % 4)) * at(SPARK_SPREAD);
            float twinkle = 0.55f + 0.45f * Mth.sin(frame * 1.5f + i * 2.1f);
            strip(Mth.cos(angle) * r, Mth.sin(angle) * r - 0.02f * (frame - 42), 1, 0, 0.035f, 0.035f, 0.74f, colours[SWIRL], light * twinkle);
        }
    }

    private void face(Sprite s, float half, float near, float turn, float[] c, float a) {
        if (a <= 0) return;
        float h = half * unit, cos = h * Mth.cos(turn), sin = h * Mth.sin(turn);
        corner(-cos + sin, -sin - cos, near, s.u0(), s.v1(), c, a);
        corner(cos + sin, sin - cos, near, s.u1(), s.v1(), c, a);
        corner(cos - sin, sin + cos, near, s.u1(), s.v0(), c, a);
        corner(-cos - sin, -sin + cos, near, s.u0(), s.v0(), c, a);
    }

    private void strip(float x, float y, float dx, float dy, float length, float width, float near, float[] c, float a) {
        Sprite s = WarpCloseTextures.dot;
        float lx = dx * length * unit, ly = dy * length * unit, wx = -dy * width * unit, wy = dx * width * unit;
        x *= unit;
        y *= unit;
        corner(x - lx - wx, y - ly - wy, near, s.u0(), s.v1(), c, a);
        corner(x + lx - wx, y + ly - wy, near, s.u1(), s.v1(), c, a);
        corner(x + lx + wx, y + ly + wy, near, s.u1(), s.v0(), c, a);
        corner(x - lx + wx, y - ly + wy, near, s.u0(), s.v0(), c, a);
    }

    private void corner(float x, float y, float near, float u, float v, float[] c, float a) {
        near = 1 - (1 - near) * nearer;
        out.addVertex((centre.x + right.x * x + up.x * y) * near, (centre.y + right.y * x + up.y * y) * near,
                (centre.z + right.z * x + up.z * y) * near).setUv(u, v).setColor(c[0], c[1], c[2], Mth.clamp(a * strength, 0, 1));
    }
}
