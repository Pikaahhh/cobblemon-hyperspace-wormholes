package dev.hyperspacewormholes.wormholes.ride;

import org.jetbrains.annotations.Nullable;

/**
 * The tunnel's shared layout, worked out the same way on both sides from the ride's seed: where each wormhole and ring
 * is, plus its colour, rings and rainbow.
 */
public final class WarpTunnel {
    public static final float TUNNEL_RADIUS = 34f;
    public static final float MAX_OFFSET = 21f;
    public static final float CRUISE_SPEED = 1.6f;
    public static final float BOOST_SPEED = 2.9f;
    public static final float MAX_SPEED = 3.3f;
    public static final int SHINY_RINGS = 4;
    public static final double MAX_WARP_DISTANCE = 29_000_000;

    private static final double FIRST_WORMHOLE = 240;
    private static final double SPACING = 210;
    private static final double SPACING_JITTER = 70;
    private static final float LATERAL_SPREAD = 11f;
    private static final double RING_CHANCE = 0.5;
    private static final double SHINY_RING_CHANCE = 0.125;
    private static final double FREE_RING_EVERY = 15_000;
    private static final double WARP_DISTANCE_SCALE = 2;
    private static final double GOLD_RINGS_PER_GAP = 0.8;
    private static final double GOLD_RING_BONUS_UNTIL = 6_000;
    private static final double GOLD_RING_THINNING_EVERY = 20_000;
    private static final float GOLD_RING_SPREAD = 16f;

    public record GoldRing(int index, double distance, float x, float y) {
        public static final float RADIUS = 3.2f;
        public static final float TOUCH_RADIUS = 3.8f;
    }

    private WarpTunnel() {}

    public record Wormhole(int index, double distance, float x, float y, int rings, WormholeType type, float scale,
                           boolean rainbow) {
        public Wormhole(int index, double distance, float x, float y, int rings, WormholeType type) {
            this(index, distance, x, y, rings, type, 1f, false);
        }

        public Wormhole(int index, double distance, float x, float y, int rings, WormholeType type, float scale) {
            this(index, distance, x, y, rings, type, scale, false);
        }

        public float radius() {
            return 6f * scale;
        }
    }

    public static Wormhole wormhole(long seed, int index) {
        long h = mix(seed ^ (index * 0x9E3779B97F4A7C15L));
        double jitter = (unit(h) * 2 - 1) * SPACING_JITTER;
        h = mix(h);
        double angle = unit(h) * Math.PI * 2;
        h = mix(h);
        double r = Math.sqrt(unit(h)) * LATERAL_SPREAD;
        h = mix(h);
        double distance = FIRST_WORMHOLE + index * SPACING + jitter;
        h = mix(h);
        WormholeType type = WormholeType.pick(unit(h), warpDistance(distance));
        h = mix(h);
        int rings = rings(h, type == WormholeType.PURPLE ? SHINY_RINGS - 1 : SHINY_RINGS, warpDistance(distance));
        return new Wormhole(index, distance,
                (float) (Math.cos(angle) * r), (float) (Math.sin(angle) * r), rings, type, 1f, type == WormholeType.GOLD);
    }

    private static int rings(long h, int max, double warpDistance) {
        int rings = Math.min(max, (int) (warpDistance / FREE_RING_EVERY));
        while (rings < max && unit(h = mix(h)) < (rings + 1 == SHINY_RINGS ? SHINY_RING_CHANCE : RING_CHANCE)) rings++;
        return rings;
    }

    private static double goldRingsPerGap(double warpDistance) {
        if (warpDistance < GOLD_RING_BONUS_UNTIL) return GOLD_RINGS_PER_GAP * 1.5;
        return GOLD_RINGS_PER_GAP * Math.pow(0.75, Math.floor(warpDistance / GOLD_RING_THINNING_EVERY));
    }

    public static @Nullable GoldRing goldRing(long seed, int slot) {
        int gap = slot >> 1, half = slot & 1;
        double from = gap == 0 ? 0 : wormhole(seed, gap - 1).distance();
        double to = wormhole(seed, gap).distance();
        long h = mix(seed ^ 0x60D5L ^ (slot * 0xD1B54A32D192ED03L));
        if (unit(h) >= goldRingsPerGap(warpDistance((from + to) / 2)) / 2) return null;
        h = mix(h);
        double distance = from + (to - from) * (0.2 + 0.5 * half + 0.25 * unit(h));
        h = mix(h);
        double angle = unit(h) * Math.PI * 2;
        h = mix(h);
        double r = Math.sqrt(unit(h)) * GOLD_RING_SPREAD;
        return new GoldRing(slot, distance, (float) (Math.cos(angle) * r), (float) (Math.sin(angle) * r));
    }

    public static double goldRingPassedAt(long seed, int slot) {
        GoldRing ring = goldRing(seed, slot);
        if (ring != null) return ring.distance();
        int gap = slot >> 1;
        double from = gap == 0 ? 0 : wormhole(seed, gap - 1).distance();
        double to = wormhole(seed, gap).distance();
        return (slot & 1) == 0 ? (from + to) / 2 : to;
    }

    public static int firstGoldSlotAtOrAfter(double progress) {
        return firstIndexAtOrAfter(progress) * 2;
    }

    public static boolean touches(GoldRing ring, float offX, float offY) {
        float dx = ring.x() - offX, dy = ring.y() - offY;
        return dx * dx + dy * dy <= GoldRing.TOUCH_RADIUS * GoldRing.TOUCH_RADIUS;
    }

    public static double warpDistance(double moved) {
        return moved * WARP_DISTANCE_SCALE;
    }

    public static int firstIndexAtOrAfter(double progress) {
        return Math.max(0, (int) Math.floor((progress - FIRST_WORMHOLE - SPACING_JITTER) / SPACING));
    }

    public static boolean touches(Wormhole w, float offX, float offY) {
        float dx = w.x() - offX, dy = w.y() - offY;
        float touch = w.radius() * 1.5f;
        return dx * dx + dy * dy <= touch * touch;
    }

    public static float bendX(float z, float time) {
        float a = (float) (Math.sin(time * 0.0091) * 0.22 + Math.sin(time * 0.0233 + 1.3) * 0.12);
        return a * z * z / 200f;
    }

    public static float bendY(float z, float time) {
        float a = (float) (Math.sin(time * 0.0127 + 2.1) * 0.16 + Math.sin(time * 0.0301 + 0.4) * 0.08);
        return a * z * z / 200f;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }
}
