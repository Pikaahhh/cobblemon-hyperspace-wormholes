package dev.hyperspacewormholes.wormholes.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.ride.RideLegend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * A wormhole closing behind the legend at the end of a ride, as in Ultra Sun and Ultra Moon: the ride's own motion into
 * it and the clock everything else runs on, counted in the game's frames (30 a second) from the touch, so it moves the
 * same at any frame rate.
 * <p>
 * The legend is drawn to the wormhole's middle while it slows down, and shrinks to nothing (LEGEND). The wormhole
 * shrinks (HOLE) and fades into the burst (HOLE_LIGHT). WarpCloseLook draws the light and WarpWhite the white disc it
 * swells into; the plain white after that (WHITE) is RideClient's.
 * <p>
 * The clock eases off towards HOLD_FRAME, before the burst, until the server has answered the touch by whiting the ride
 * out, and then runs on. If the white would be late for the arrival, as it can be for a passenger, it runs a little
 * faster. If the server doesn't answer within RELEASE_TICKS the ride goes on and the close fades away over FADE_TICKS.
 */
public final class WarpEntryClose {
    private static final float FRAMES_PER_TICK = 30f / 20f;
    private static final float HOLD_FROM = 8f, HOLD_FRAME = 11.5f;
    private static final float WHITE_FRAME = 51f;
    private static final float RATE_EASE_TICKS = 3f;
    private static final float FASTEST = 1.5f;
    private static final int RELEASE_TICKS = 20;
    private static final float FADE_TICKS = 8f;
    private static final float PULL = 0.6f;
    private static final float SLOW_TICKS = 1.8f;
    private static final float ROLL_EASE = 0.35f;
    private static final SmoothCurve LEGEND = new SmoothCurve(0, 1, 8, 1, 12, 0.8f, 14, 0.4f, 16, 0.16f, 20, 0);
    private static final SmoothCurve HOLE = new SmoothCurve(0, 1, 4, 0.95f, 10, 0.75f, 14, 0.4f);
    private static final SmoothCurve HOLE_LIGHT = new SmoothCurve(11, 1, 14, 0);
    private static final SmoothCurve TRAIL = new SmoothCurve(0, 1, 10, 0);
    private static final SmoothCurve WHITE = new SmoothCurve(50, 0, 50.8f, 1);
    private static final SmoothCurve QUIET = new SmoothCurve(31, 0, 36, 0.25f, 46, 0.75f, 51, 1);

    private static int hole = -1;
    private static float touchedAt, answeredAt, answeredFrame, answeredRate, rate, releasedAt;
    private static boolean answered, released;
    private static double fromProgress;
    private static float speed, holeX, holeY, fromX, fromY, velX, velY, fromRoll, velRoll;

    private WarpEntryClose() {}

    static void reset() {
        hole = -1;
        answered = released = false;
    }

    /** The ride touched this wormhole at this time, there along and across the tunnel, moving and banking that fast a tick. */
    static void touch(int index, float time, float atX, float atY, double progress, float speedThen, float offX, float offY,
                      float offVelX, float offVelY, float roll, float rollVel) {
        if (playing()) return;
        reset();
        hole = index;
        touchedAt = time;
        holeX = atX;
        holeY = atY;
        fromProgress = progress;
        speed = speedThen;
        fromX = offX;
        fromY = offY;
        velX = offVelX;
        velY = offVelY;
        fromRoll = roll;
        velRoll = rollVel;
    }

    /** Once a ride tick. ticksToWhite is how long until the white the server called is full, or less than 0 without one. */
    static void tick(float time, float ticksToWhite) {
        if (hole < 0 || answered || time < touchedAt) return;
        if (released) {
            if (time - releasedAt > FADE_TICKS) reset();
        } else if (ticksToWhite >= 0) {
            answeredFrame = waiting(time);
            answeredRate = waitingRate(time);
            float left = Math.max(1, ticksToWhite) * FRAMES_PER_TICK;
            rate = FRAMES_PER_TICK * Math.max(1, Math.min(FASTEST, (WHITE_FRAME - answeredFrame) / left));
            answeredAt = time;
            answered = true;
        } else if (time - touchedAt > RELEASE_TICKS) {
            released = true;
            releasedAt = time;
        }
    }

    private static float waiting(float time) {
        float f = Math.max(0, time - touchedAt) * FRAMES_PER_TICK, over = f - HOLD_FROM, room = HOLD_FRAME - HOLD_FROM;
        return over <= 0 ? f : HOLD_FROM + room * (1 - exp(-over / room));
    }

    private static float waitingRate(float time) {
        float over = Math.max(0, time - touchedAt) * FRAMES_PER_TICK - HOLD_FROM;
        return FRAMES_PER_TICK * (over <= 0 ? 1 : exp(-over / (HOLD_FRAME - HOLD_FROM)));
    }

    private static float exp(float v) {
        return (float) Math.exp(v);
    }

    /** Whether the ride is being drawn in rather than flown. */
    public static boolean playing() {
        return hole >= 0 && !released;
    }

    /** Whether it was already being drawn in at this time. */
    static boolean flying(float time) {
        return playing() && time >= touchedAt;
    }

    /** Whether the wormhole with this index is the one closing. */
    public static boolean of(int index) {
        return hole >= 0 && hole == index;
    }

    /** Whether this far along the tunnel is on this side of the wormhole that's closing. */
    public static boolean before(double distance) {
        return hole >= 0 && distance < fromProgress;
    }

    static double progress(float time) {
        float t = since(time) / SLOW_TICKS;
        return fromProgress + speed * SLOW_TICKS * (2 - (2 + t) * exp(-t));
    }

    static float speed(float time) {
        float t = since(time) / SLOW_TICKS;
        return speed * (1 + t) * exp(-t);
    }

    static float offX(float time) {
        return drawn(holeX, fromX - holeX, velX, PULL, since(time));
    }

    static float offY(float time) {
        return drawn(holeY, fromY - holeY, velY, PULL, since(time));
    }

    static float roll(float time) {
        return drawn(0, fromRoll, velRoll, ROLL_EASE, since(time));
    }

    private static float since(float time) {
        return Math.max(0, time - touchedAt);
    }

    /** A critically damped pull to a place, from this far off it at this speed. */
    private static float drawn(float to, float off, float vel, float pull, float t) {
        return to + (off + (vel + pull * off) * t) * exp(-pull * t);
    }

    /** Game frames since the touch, or -1. */
    public static float frame(float partialTick) {
        return hole < 0 ? -1 : frameAt(RideClient.time(partialTick));
    }

    private static float frameAt(float time) {
        if (!answered || time <= answeredAt) return waiting(time);
        float t = time - answeredAt;
        return answeredFrame + rate * t + (answeredRate - rate) * RATE_EASE_TICKS * (1 - exp(-t / RATE_EASE_TICKS));
    }

    /** How strongly the close shows: 1, fading to 0 if the server never answered the touch. */
    public static float strength(float partialTick) {
        if (hole < 0) return 0;
        if (!released) return 1;
        float t = Math.max(0, Math.min(1, (RideClient.time(partialTick) - releasedAt) / FADE_TICKS));
        return 1 - t * t * (3 - 2 * t);
    }

    /** Whether the light is Solgaleo's gold and red rather than Lunala's blue and violet. */
    public static boolean sun() {
        LocalPlayer player = Minecraft.getInstance().player;
        return RideClient.mount() == RideLegend.SOLGALEO || player != null && player.getVehicle() instanceof PokemonEntity legend
                && legend.getPokemon().getAspects().contains("dusk-fusion");
    }

    /** The size to draw the legend and its riders at, 0 once they're gone. */
    public static float legendScale(float partialTick) {
        return hole < 0 ? 1 : 1 - strength(partialTick) * (1 - LEGEND.at(frame(partialTick)));
    }

    /** How much of the light trail behind the legend, and of a wormhole's name, still shows. */
    public static float trail(float partialTick) {
        return hole < 0 ? 1 : 1 - strength(partialTick) * (1 - TRAIL.at(frame(partialTick)));
    }

    /** The size to draw the wormhole at. */
    public static float holeScale(float partialTick) {
        return HOLE.at(frame(partialTick));
    }

    /** How much of the wormhole still shows. */
    public static float holeLight(float partialTick) {
        return strength(partialTick) * HOLE_LIGHT.at(frame(partialTick));
    }

    /** Whether the server has answered, so the white comes at the end. */
    public static boolean whitening() {
        return hole >= 0 && answered;
    }

    /** How far the sounds have died away towards the end, 0 to 1. */
    static float quiet(float partialTick) {
        return answered ? QUIET.at(frame(partialTick)) : 0;
    }

    /** The plain white over everything at the very end, once WarpWhite's disc covers the picture, 0 to 1. */
    static float white(float partialTick) {
        return answered ? WHITE.at(frame(partialTick)) : 0;
    }
}
