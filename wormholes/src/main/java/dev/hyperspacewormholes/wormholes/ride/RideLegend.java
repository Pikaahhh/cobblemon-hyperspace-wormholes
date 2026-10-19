package dev.hyperspacewormholes.wormholes.ride;

/**
 * Which legend you're riding, which tints the tunnel: Solgaleo warm, Lunala violet, Necrozma rainbow.
 */
public enum RideLegend {
    NONE, SOLGALEO, LUNALA, NECROZMA;

    public static RideLegend byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : NONE;
    }
}
