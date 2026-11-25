package dev.hyperspacewormholes.wormholes.client;

import dev.hyperspacewormholes.wormholes.ride.WarpTunnel;
import net.minecraft.util.Mth;

/**
 * The opening shot of every Ultra Warp Ride, with Ultra Sun and Ultra Moon's camera path, lens and timing: for the
 * first WarpTunnel.OPENING_TICKS of a ride the legend flies down a corridor of light. The camera sits beside its flight
 * line while it comes past (70 frames), holds on it (15), swings round behind it and falls back as it flies off towards
 * the hole at the corridor's end (102), then flies in after it over HANDOVER frames into the ride camera (RideCamera).
 * Nobody steers until it ends (RideClient), and the server starts the stamina clock after it too.
 * <p>
 * Everything is in game units (RideCamera.unit turns them into blocks), x to the legend's left, y up and z forward, at
 * 30 frames a second. The legend stays where the ride holds it: the camera and the corridor move round it. The camera
 * keys are its eye and the point it looks at, at the start, the hold and the far end, for Solgaleo and for Lunala. The
 * corridor is a cone from its mouth (the hole's rim, mouth()) back to its near end (nearEnd()), its radius scaled by
 * scale(). RideOpeningRenderer draws it.
 */
public final class RideOpening {
    public static final float FOV = 35f;
    public static final float MOUTH_RADIUS = 781.5f;
    public static final float NEAR_RADIUS = 3023.5f;
    /** The game frame the tunnel starts to show through the corridor's mouth. */
    public static final float HOLE_OPENS = 161f;
    private static final float FRAMES_PER_TICK = 30f / 20f;
    private static final float SIDE = 69f;
    private static final float HOLD = 85f;
    private static final float SWING = 101f;
    private static final float HANDOVER_FROM = 181f;
    private static final float HANDOVER = 30f;
    private static final float TURN = 5f;
    private static final float FALL_BACK = 4f;
    private static final float LEGEND_FROM = -6000f;
    private static final float LEGEND_SPEED = 6000f / 197f;
    private static final float CORRIDOR_FROM = -34293.8f;
    private static final float CORRIDOR_SPEED = 161.26f;
    private static final float[][] LUNALA = {
            {253.007f, 142.965f, 1402.08f, -90.019f, 51.626f, 1221.8f},
            {348.763f, 184.353f, 201.358f, -11.414f, 88.448f, 12.064f},
            {0f, 209.163f, -4007.1f, 0f, 97.724f, 558.125f},
    };
    private static final float[][] SOLGALEO = {
            {409.228f, 104.529f, 747.54f, -24.016f, 96.458f, 612.135f},
            {409.228f, 104.529f, 231.3f, -24.016f, 96.458f, 95.895f},
            {0f, 249.448f, -4007.1f, 0f, 97.724f, 558.126f},
    };
    /** Frame, value and slope of the corridor's radius scale. */
    private static final float[][] SCALE = {
            {0, 1.9054f, -0.00794f}, {153, 0.5f, 0f}, {178, 0.5f, 0.00201f}, {184, 0.81343f, 0.12018f},
            {204, 5.82474f, 0.29968f}, {205, 6.12299f, 0.1491f}, {206, 6.12299f, 0f},
    };
    private static final float STRETCH_FROM = 83f;
    private static final float STRETCH_STEP = 6f;
    private static final float STRETCH_SHARE = 0.375f;
    private static final float[] STRETCH = {
            0, 856, 4714, 11815, 22374, 36682, 55125, 78214, 106626, 141279, 183456, 233549, 288351, 345042, 401324,
            455192, 504775, 548221, 583596, 608782, 621347, 622247,
    };

    private RideOpening() {}

    public static boolean playing(float partialTick) {
        return RideClient.isActive() && RideClient.time(partialTick) < WarpTunnel.OPENING_TICKS + 1;
    }

    /** The game frame the shot is on. */
    public static float frame(float partialTick) {
        return RideClient.time(partialTick) * FRAMES_PER_TICK;
    }

    /** How far the camera has gone from the opening's shot to the ride camera, 0 to 1. */
    public static float toRide(float partialTick) {
        if (!playing(partialTick)) return 1f;
        float x = Mth.clamp((frame(partialTick) - HANDOVER_FROM) / HANDOVER, 0f, 1f);
        float left = 1 - x;
        return 1 - left * left * left * (1 + 3 * x);
    }

    /** The camera's eye and the point it looks at, from the legend. */
    public static float[] camera(float frame, boolean solgaleo) {
        float[][] keys = solgaleo ? SOLGALEO : LUNALA;
        float[] out = new float[6];
        if (frame < HOLD) {
            float x = Math.min(1f, frame / SIDE);
            for (int i = 0; i < 6; i++) out[i] = Mth.lerp(x, keys[0][i], keys[1][i]);
            return out;
        }
        float x = (frame - HOLD) / SWING;
        swing(keys[1], keys[2], 0, x, out);
        swing(keys[1], keys[2], 3, x, out);
        return out;
    }

    private static void swing(float[] from, float[] to, int at, float x, float[] out) {
        double fromR = Math.hypot(from[at], from[at + 2]), toR = Math.hypot(to[at], to[at + 2]);
        double ux = from[at] / fromR, uz = from[at + 2] / fromR, vx = to[at] / toR, vz = to[at + 2] / toR;
        double angle = Math.acos(Math.max(-1, Math.min(1, ux * vx + uz * vz)));
        if (uz * vx - ux * vz <= 0) angle = Math.PI * 2 - angle;
        angle *= (1 - Math.exp(-TURN * x)) / (1 - Math.exp(-TURN));
        double r = fromR + (toR - fromR) * (Math.exp(FALL_BACK * x) - 1) / (Math.exp(FALL_BACK) - 1);
        out[at] = (float) ((ux * Math.cos(angle) + uz * Math.sin(angle)) * r);
        out[at + 1] = Mth.lerp(x, from[at + 1], to[at + 1]);
        out[at + 2] = (float) ((uz * Math.cos(angle) - ux * Math.sin(angle)) * r);
    }

    private static float legend(float frame) {
        return LEGEND_FROM + LEGEND_SPEED * (frame + 1);
    }

    private static float corridor(float frame) {
        return CORRIDOR_FROM + CORRIDOR_SPEED * frame;
    }

    /** How far ahead of the legend the corridor's mouth is. */
    public static float mouth(float frame) {
        return -3019.6f - corridor(frame) - legend(frame);
    }

    /** How far ahead of the legend the corridor's near end is (it is behind, so this is negative). */
    public static float nearEnd(float frame) {
        return -43511f - corridor(frame) - STRETCH_SHARE * stretch(frame) - legend(frame);
    }

    private static float stretch(float frame) {
        float x = Mth.clamp((frame - STRETCH_FROM) / STRETCH_STEP, 0f, STRETCH.length - 1);
        int i = Math.min((int) x, STRETCH.length - 2);
        float s = x - i;
        float a = STRETCH[Math.max(0, i - 1)], b = STRETCH[i], c = STRETCH[i + 1],
                d = STRETCH[Math.min(STRETCH.length - 1, i + 2)];
        return b + 0.5f * s * (c - a + s * (2 * a - 5 * b + 4 * c - d + s * (3 * (b - c) + d - a)));
    }

    /** How wide the corridor is, as a share of its own size. */
    public static float scale(float frame) {
        if (frame <= SCALE[0][0]) return SCALE[0][1];
        for (int i = 1; i < SCALE.length; i++) {
            if (frame > SCALE[i][0]) continue;
            float[] a = SCALE[i - 1], b = SCALE[i];
            float d = b[0] - a[0], s = (frame - a[0]) / d, s2 = s * s, s3 = s2 * s;
            return (2 * s3 - 3 * s2 + 1) * a[1] + (s3 - 2 * s2 + s) * d * a[2] + (3 * s2 - 2 * s3) * b[1]
                    + (s3 - s2) * d * b[2];
        }
        return SCALE[SCALE.length - 1][1];
    }

    public static float smooth(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3 - 2 * t);
    }
}
