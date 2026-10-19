package dev.hyperspacewormholes.wormholes.ride;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Which wormholes a ride's tunnel has: an endless run from the seed, or a fixed list on a warp stone ride.
 */
public final class TunnelLayout {
    private static final double UNCHARTED_GAP = 400;
    private static final float UNCHARTED_SCALE = 2.2f;

    private final long seed;
    private final @Nullable List<WarpTunnel.Wormhole> fixed;
    private final List<String> names;
    private final @Nullable String override;

    private TunnelLayout(long seed, @Nullable List<WarpTunnel.Wormhole> fixed, List<String> names, @Nullable String override) {
        this.seed = seed;
        this.fixed = fixed;
        this.names = names;
        this.override = override;
    }

    public static TunnelLayout seeded(long seed) {
        return seeded(seed, null);
    }

    public static TunnelLayout seeded(long seed, @Nullable String override) {
        return new TunnelLayout(seed, null, List.of(), override == null || override.isEmpty() ? null : override);
    }

    public static TunnelLayout fixed(long seed, List<WarpTunnel.Wormhole> wormholes, List<String> names) {
        return new TunnelLayout(seed, List.copyOf(wormholes), List.copyOf(names), null);
    }

    public @Nullable String override() {
        return override;
    }

    public boolean isFixed() {
        return fixed != null;
    }

    public WarpTunnel.Wormhole wormhole(int index) {
        if (fixed == null) {
            WarpTunnel.Wormhole w = WarpTunnel.wormhole(seed, index);
            return override != null ? new WarpTunnel.Wormhole(w.index(), w.distance(), w.x(), w.y(), w.rings(), WormholeType.WHITE, w.scale(), false) : w;
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
