package dev.hyperspacewormholes.wormholes.ride;

/**
 * Ride stamina. Drains over time, golden rings give some back and speed the legend up, and when it hits zero the
 * legend gets dragged into the next wormhole.
 */
public final class Stamina {
    public static final int SECTIONS = 18;
    private static final int MAX_HALVES = SECTIONS * 2;
    public static final int DRAIN_INTERVAL_TICKS = 100;
    public static final double RING_BONUS = 0.03;

    private double halves = MAX_HALVES;
    private int rings;

    public void tick(int rideTick) {
        if (rideTick % DRAIN_INTERVAL_TICKS == 0) halves = Math.max(0, halves - 2 * (1 + rings * RING_BONUS));
    }

    public void collectRing() {
        rings++;
        if (halves > 0) halves = Math.min(MAX_HALVES, halves + 2);
    }

    public int halves() {
        return (int) Math.ceil(halves);
    }

    public float speedFactor() {
        return (float) (1 + rings * RING_BONUS);
    }

    public boolean exhausted() {
        return halves <= 0;
    }
}
