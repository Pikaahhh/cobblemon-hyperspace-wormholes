package dev.hyperspacewormholes.wormholes.ride;

import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A wormhole's type, which you can tell by its colour; decides where it goes and what's waiting there.
 * With the game's colours (WormholeConfig.gameWormholeColours) a Pokemon wormhole is red, green, yellow or blue, a quarter each, and leads to that colour's Ultra Space Wilds world: red Cliff World, green Rocky (Crag) World, yellow Cave World, blue Water World. There's no gold wormhole then; legendaries come from the coloured ones, more often the more rings they have.
 * Otherwise the colour is the difficulty (green, orange, red), with gold wormholes to legendaries. New types go at the end: types are saved and sent by ordinal.
 */
public enum WormholeType {
    BLUE(0.25f, 0.55f, 1.00f, 0, 0),
    RED(1.00f, 0.22f, 0.20f, 0, 0),
    GOLD(1.00f, 0.78f, 0.20f, 0, 12_000),
    GREEN(0.30f, 1.00f, 0.42f, 0, 0),
    WHITE(0.90f, 0.92f, 1.00f, 0, 9_000),
    PURPLE(0.72f, 0.34f, 1.00f, 21, 4_000),
    ORANGE(1.00f, 0.55f, 0.10f, 0, 0),
    YELLOW(1.00f, 0.88f, 0.18f, 0, 0),
    WATER(0.10f, 0.32f, 1.00f, 0, 0);

    private static final List<WormholeType> WILDS_COLOURS = List.of(RED, GREEN, YELLOW, WATER);
    private static final int DIFFICULTY_WEIGHT = 190;
    private static final double STAGE_EVERY = 20_000;
    private static final int MAX_STAGE = 4;

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

    public @Nullable ResourceKey<Level> wildsWorld() {
        return switch (this) {
            case RED -> Wormholes.WILDS_CLIFF;
            case GREEN -> Wormholes.WILDS_CRAG;
            case YELLOW -> Wormholes.WILDS_CAVE;
            case WATER -> Wormholes.WILDS_WATER;
            default -> null;
        };
    }

    public static WormholeType ofWilds(ResourceKey<Level> world) {
        for (WormholeType type : WILDS_COLOURS) {
            if (type.wildsWorld() == world) return type;
        }
        return GREEN;
    }

    public static WormholeType pick(double roll, double warpDistance, boolean gameColours) {
        int stage = Math.min(MAX_STAGE, 1 + (int) (warpDistance / STAGE_EVERY));
        double white = warpDistance >= WHITE.minWarpDistance ? stage / 8.0 : 0;
        double gold = !gameColours && warpDistance >= GOLD.minWarpDistance ? stage / 12.0 : 0;
        if (roll < white) return WHITE;
        if (roll < white + gold) return GOLD;
        double rest = (roll - white - gold) / (1 - white - gold);
        double purple = warpDistance >= PURPLE.minWarpDistance ? PURPLE.weight / (double) (PURPLE.weight + DIFFICULTY_WEIGHT) : 0;
        if (rest < purple) return PURPLE;
        double regular = (rest - purple) / (1 - purple);
        if (gameColours) return WILDS_COLOURS.get(Math.min(WILDS_COLOURS.size() - 1, (int) (regular * WILDS_COLOURS.size())));
        return difficulty(regular, warpDistance);
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
