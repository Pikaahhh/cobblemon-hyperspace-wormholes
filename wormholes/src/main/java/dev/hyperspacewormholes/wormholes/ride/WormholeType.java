package dev.hyperspacewormholes.wormholes.ride;

/**
 * A wormhole's type, which you can tell by its colour. Decides where it goes and what's waiting there.
 */
public enum WormholeType {
    BLUE(0.25f, 0.55f, 1.00f, 0, 0),
    RED(1.00f, 0.22f, 0.20f, 0, 0),
    GOLD(1.00f, 0.78f, 0.20f, 0, 12_000),
    GREEN(0.30f, 1.00f, 0.42f, 0, 0),
    WHITE(0.90f, 0.92f, 1.00f, 0, 9_000),
    PURPLE(0.72f, 0.34f, 1.00f, 21, 4_000),
    ORANGE(1.00f, 0.55f, 0.10f, 0, 0);

    public final float r, g, b;
    private final int weight;
    private final double minWarpDistance;

    WormholeType(float r, float g, float b, int weight, double minWarpDistance) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.weight = weight;
        this.minWarpDistance = minWarpDistance;
    }

    public static WormholeType byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : BLUE;
    }

    public int difficulty() {
        return switch (this) {
            case GREEN -> 1;
            case ORANGE -> 2;
            case RED -> 3;
            default -> 0;
        };
    }

    private static final int DIFFICULTY_WEIGHT = 190;
    private static final double STAGE_EVERY = 20_000;
    private static final int MAX_STAGE = 4;

    public static WormholeType pick(double roll, double warpDistance) {
        int stage = Math.min(MAX_STAGE, 1 + (int) (warpDistance / STAGE_EVERY));
        double white = warpDistance >= WHITE.minWarpDistance ? stage / 8.0 : 0;
        double gold = warpDistance >= GOLD.minWarpDistance ? stage / 12.0 : 0;
        if (roll < white) return WHITE;
        if (roll < white + gold) return GOLD;
        double rest = (roll - white - gold) / (1 - white - gold);
        double purple = warpDistance >= PURPLE.minWarpDistance ? PURPLE.weight / (double) (PURPLE.weight + DIFFICULTY_WEIGHT) : 0;
        if (rest < purple) return PURPLE;
        return difficulty((rest - purple) / (1 - purple), warpDistance);
    }

    private static WormholeType difficulty(double roll, double warpDistance) {
        double km = warpDistance / 1000.0;
        double red = Math.min(0.30, 0.03 + 0.03 * km);
        double orange = Math.min(0.40, 0.15 + 0.05 * km);
        if (roll < red) return RED;
        if (roll < red + orange) return ORANGE;
        return GREEN;
    }
}
