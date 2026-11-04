package dev.hyperspacewormholes.wormholes.ride;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Which wormholes a ride's tunnel has: an endless run from the seed (in the game's colours or the difficulty ones, as
 * the server's config says), or a fixed list on a warp stone ride. A ride to one destination (a /wormhole create
 * order with ride=true) keeps the seeded run but every wormhole in it is the destination's colour with the order's
 * rings (only, onlyRings).
 */
public final class TunnelLayout {
    private static final double UNCHARTED_GAP = 400;
    private static final float UNCHARTED_SCALE = 2.2f;

    private final long seed;
    private final @Nullable List<WarpTunnel.Wormhole> fixed;
    private final List<String> names;
    private final boolean gameColours;
    private final @Nullable WormholeType only;
    private final int onlyRings;

    private TunnelLayout(long seed, @Nullable List<WarpTunnel.Wormhole> fixed, List<String> names, boolean gameColours,
                         @Nullable WormholeType only, int onlyRings) {
        this.seed = seed;
        this.fixed = fixed;
        this.names = names;
        this.gameColours = gameColours;
        this.only = only;
        this.onlyRings = onlyRings;
    }

    public static TunnelLayout seeded(long seed) {
        return seeded(seed, true);
    }

    public static TunnelLayout seeded(long seed, boolean gameColours) {
        return seeded(seed, gameColours, null, 0);
    }

    public static TunnelLayout seeded(long seed, boolean gameColours, @Nullable WormholeType only, int onlyRings) {
        return new TunnelLayout(seed, null, List.of(), gameColours, only, onlyRings);
    }

    public static TunnelLayout fixed(long seed, List<WarpTunnel.Wormhole> wormholes, List<String> names) {
        return new TunnelLayout(seed, List.copyOf(wormholes), List.copyOf(names), true, null, 0);
    }

    public boolean gameColours() {
        return gameColours;
    }

    public @Nullable WormholeType only() {
        return only;
    }

    public int onlyRings() {
        return onlyRings;
    }

    public boolean isFixed() {
        return fixed != null;
    }

    public WarpTunnel.Wormhole wormhole(int index) {
        if (fixed == null) {
            WarpTunnel.Wormhole w = WarpTunnel.wormhole(seed, index, gameColours);
            return only == null ? w : new WarpTunnel.Wormhole(w.index(), w.distance(), w.x(), w.y(), onlyRings, only, w.scale());
        }
        if (index < fixed.size()) return fixed.get(index);
        if (index == fixed.size()) {
            double last = fixed.isEmpty() ? 0 : fixed.getLast().distance();
            return new WarpTunnel.Wormhole(index, last + UNCHARTED_GAP, 0, 0, 0, WormholeType.GREEN, UNCHARTED_SCALE);
        }
        return new WarpTunnel.Wormhole(index, Double.POSITIVE_INFINITY, 0, 0, 0, WormholeType.BLUE);
    }

    public boolean isUncharted(int index) {
        return fixed != null && index == fixed.size();
    }

    public double warpDistance(double moved) {
        return fixed == null ? WarpTunnel.warpDistance(moved) : moved;
    }

    public @Nullable String name(int index) {
        return index >= 0 && index < names.size() ? names.get(index) : null;
    }

    public int firstIndexAtOrAfter(double progress) {
        return fixed == null ? WarpTunnel.firstIndexAtOrAfter(progress) : 0;
    }

    public int firstWormholeAfter(double progress) {
        int i = firstIndexAtOrAfter(progress);
        while (wormhole(i).distance() <= progress) i++;
        return i;
    }
}
