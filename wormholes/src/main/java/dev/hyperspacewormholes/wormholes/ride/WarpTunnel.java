package dev.hyperspacewormholes.wormholes.ride;

import dev.hyperspacewormholes.wormholes.GameplayConfig;
import org.jetbrains.annotations.Nullable;

/**
 * The tunnel's shared layout, worked out the same way on both sides from the ride's seed: where each wormhole and ring is,
 * plus its colour, rings and rainbow. With the game's colours the rings are rolled from the light-years flown (a block of
 * tunnel is a light-year): none before 1,000, then one, two or the top tier (GAME_TOP_RINGS) by GAME_RING_ODDS for each
 * 500 light-years up to 4,500. band() is the game's distance step (one per 500, at most 10) that the shiny and legendary
 * odds go by.
 */
public final class WarpTunnel {
    public static final float TUNNEL_RADIUS = 34f;
    public static final float MAX_OFFSET = 21f;
    public static final float CRUISE_SPEED = 1.6f;
    public static final float BOOST_SPEED = 2.9f;
    public static final float MAX_SPEED = 3.3f;
    public static final int SHINY_RINGS = 4;
    public static final int GAME_TOP_RINGS = 3;
    public static final double MAX_WARP_DISTANCE = 29_000_000;

    private static final double FIRST_WORMHOLE = 240;
    private static final double SPACING = 210;
    private static final double SPACING_JITTER = 70;
    private static final float LATERAL_SPREAD = 11f;
    private static final double RING_CHANCE = 0.5;
    private static final double SHINY_RING_CHANCE = 0.125;
    private static final double FREE_RING_EVERY = 15_000;
    private static final double WARP_DISTANCE_SCALE = 2;
    private static final double LIGHT_YEAR_BAND = 500;
    private static final int MAX_BAND = 10;
    private static final double FIRST_RINGS_AT = 1_000;
    private static final int[][] GAME_RING_ODDS = {
            {78, 20, 2, 0}, {66, 30, 4, 0}, {52, 40, 8, 0}, {33, 50, 16, 1}, {32, 50, 16, 2}, {31, 50, 16, 3},
            {30, 50, 16, 4}, {29, 50, 16, 5}};
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

    public static Wormhole wormhole(long seed, int index, boolean gameColours) {
        long h = mix(seed ^ (index * 0x9E3779B97F4A7C15L));
        double jitter = (unit(h) * 2 - 1) * SPACING_JITTER;
        h = mix(h);
        double angle = unit(h) * Math.PI * 2;
        h = mix(h);
        double r = Math.sqrt(unit(h)) * LATERAL_SPREAD;
        h = mix(h);
        double distance = FIRST_WORMHOLE + index * SPACING + jitter;
        h = mix(h);
        WormholeType type = WormholeType.pick(unit(h), warpDistance(distance), gameColours);
        h = mix(h);
        int rings = gameColours && type != WormholeType.PURPLE ? gameRings(h, lightYears(warpDistance(distance)))
                : rings(h, type == WormholeType.PURPLE ? SHINY_RINGS - 1 : SHINY_RINGS, warpDistance(distance));
        return new Wormhole(index, distance,
                (float) (Math.cos(angle) * r), (float) (Math.sin(angle) * r), rings, type, 1f, type == WormholeType.GOLD);
    }

    private static int rings(long h, int max, double warpDistance) {
        int rings = Math.min(max, (int) (warpDistance / FREE_RING_EVERY));
        GameplayConfig config = GameplayConfig.get();
        double none = Math.max(config.ringWeight(0), 1.0E-6);
        while (rings < max && unit(h = mix(h)) < (rings + 1 == SHINY_RINGS ? SHINY_RING_CHANCE : RING_CHANCE) * config.ringWeight(rings + 1) / none) rings++;
        return rings;
    }

    private static int gameRings(long h, double lightYears) {
        if (lightYears < FIRST_RINGS_AT) return 0;
        int row = Math.min(GAME_RING_ODDS.length - 1, (int) ((lightYears - FIRST_RINGS_AT) / LIGHT_YEAR_BAND));
        int[] odds = GAME_RING_ODDS[row];
        GameplayConfig config = GameplayConfig.get();
        double total = 0;
        for (int i = 0; i <= GAME_TOP_RINGS; i++) total += odds[i] * config.ringWeight(i);
        if (total <= 0) return 0;
        double roll = unit(mix(h)) * total, below = odds[0] * config.ringWeight(0);
        int rings = 0;
        while (rings < GAME_TOP_RINGS && roll >= below) {
            rings++;
            below += odds[rings] * config.ringWeight(rings);
        }
        return rings;
    }

    public static double lightYears(double warpDistance) {
        return warpDistance / WARP_DISTANCE_SCALE;
    }

    public static int band(double lightYears) {
        return Math.min(MAX_BAND, (int) (lightYears / LIGHT_YEAR_BAND));
    }

    private static double goldRingsPerGap(double warpDistance) {
        if (warpDistance < GOLD_RING_BONUS_UNTIL) return GOLD_RINGS_PER_GAP * 1.5;
        return GOLD_RINGS_PER_GAP * Math.pow(0.75, Math.floor(warpDistance / GOLD_RING_THINNING_EVERY));
    }

    public static @Nullable GoldRing goldRing(long seed, int slot) {
        int gap = slot >> 1;
        return goldRing(seed, slot, gapStart(seed, gap), wormhole(seed, gap, true).distance());
    }

    private static double gapStart(long seed, int gap) {
        return gap == 0 ? 0 : wormhole(seed, gap - 1, true).distance();
    }

    private static @Nullable GoldRing goldRing(long seed, int slot, double from, double to) {
        int half = slot & 1;
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
        int gap = slot >> 1;
        double from = gapStart(seed, gap), to = wormhole(seed, gap, true).distance();
        GoldRing ring = goldRing(seed, slot, from, to);
        if (ring != null) return ring.distance();
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
