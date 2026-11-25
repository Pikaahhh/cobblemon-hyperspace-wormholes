package dev.hyperspacewormholes.wormholes.client;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The ride camera going into a wormhole, as in Ultra Sun and Ultra Moon: from the moment the ride touches a wormhole
 * the camera stops following. It eases to rest in the tunnel and stays there while the legend flies on into the
 * wormhole, until the ride ends. On the way it draws straight back, to FURTHER times the ride's distance behind the
 * legend, and sinks and moves across until the wormhole is near the middle of the picture. Drawing back starts as soon
 * as the wormhole can't be missed any more (approach), at most LEAD_TICKS before the touch, and is done PULLED_TICKS
 * after it. Its view never turns.
 * <p>
 * If the ride isn't whited out within RELEASE_TICKS, or the touch never comes, it goes back to following over
 * RETURN_TICKS. heldBack is how far the camera is behind its usual place, so the tunnel renderer keeps the walls, rings
 * and the wormhole drawn around it.
 */
public final class WarpEntryCamera {
    /** How many times as far behind the legend as in the ride the close is seen from. */
    public static final float FURTHER = 1.6f;
    static final int LEAD_TICKS = 5;
    private static final float PULLED_TICKS = 6.5f;
    private static final float CENTRING = 0.45f;
    private static final float SINKING = 0.85f;
    private static final int RELEASE_TICKS = 20;
    private static final int MISSED_TICKS = 10;
    private static final float COAST_TICKS = 0.9f;
    private static final float COAST = 1.6f;
    private static final float RETURN_TICKS = 8f;

    private static boolean pulling, entered, returning;
    private static float pullFrom, pullTicks, touchDue, enteredAt, returnAt;
    private static double entryProgress;
    private static float holeX, holeY, entryX, entryY, entrySpeed, entryVelX, entryVelY, coastTicks;

    private WarpEntryCamera() {}

    static void reset() {
        pulling = entered = returning = false;
    }

    /** Once a ride tick, after the ride has moved: lets go of a stuck hold. */
    static void tick(float time, boolean whitingOut) {
        if (!pulling) return;
        if (returning) {
            if (time - returnAt > RETURN_TICKS) reset();
        } else if (entered ? !whitingOut && time - enteredAt > RELEASE_TICKS : time - touchDue > MISSED_TICKS) {
            returning = true;
            returnAt = time;
        }
    }

    /** From this time on the ride can't miss the wormhole ahead, and touches it at that one. */
    static void approach(float time, float touch) {
        if (!pulling) pull(time, touch);
    }

    private static void pull(float time, float touch) {
        pulling = true;
        pullFrom = time;
        touchDue = touch;
        pullTicks = touch + PULLED_TICKS - time;
    }

    /** The ride touched a wormhole there at this time, distance along the tunnel and place across it, moving that fast a tick. */
    static void enter(float time, float atX, float atY, double progress, float offX, float offY, float speed, float velX,
                      float velY) {
        if (entered) return;
        if (!pulling) pull(time, time);
        entered = true;
        enteredAt = time;
        holeX = atX;
        holeY = atY;
        entryProgress = progress;
        entryX = offX;
        entryY = offY;
        entrySpeed = speed;
        entryVelX = velX;
        entryVelY = velY;
        coastTicks = Math.min(COAST_TICKS, COAST / Math.max(speed, 0.1f));
    }

    /** How long at the touch's speed the camera has coasted on since: it eases to rest, over COAST_TICKS or COAST blocks. */
    private static float coast(float since) {
        float t = since / coastTicks;
        return coastTicks * (2 - (2 + t) * (float) Math.exp(-t));
    }

    private static float across(float to, float from, float vel, float since) {
        return to + (from - to + (vel + CENTRING * (from - to)) * since) * (float) Math.exp(-CENTRING * since);
    }

    /** How much it's away from the ride camera's moves: 1, easing to 0 as it goes back to following. */
    private static float kept(float time) {
        if (!returning) return 1f;
        float t = Mth.clamp((time - returnAt) / RETURN_TICKS, 0f, 1f);
        return 1 - t * t * t * (10 - t * (15 - 6 * t));
    }

    private static float drawn(float time) {
        float t = Mth.clamp((time - pullFrom) / pullTicks, 0f, 1f);
        return t * t * t * (10 - t * (15 - 6 * t));
    }

    private static float drawnBack(float time) {
        return (FURTHER - 1) * RideCamera.distance() * drawn(time);
    }

    /** How far it has sunk, towards looking straight at the wormhole's middle. */
    private static float sunk(float time) {
        return SINKING * RideCamera.above(FURTHER) * drawn(time);
    }

    private static boolean held(float time) {
        return entered && time >= enteredAt;
    }

    public static float heldBack(float partialTick) {
        if (!pulling || !RideClient.isActive()) return 0f;
        float time = RideClient.time(partialTick), back = drawnBack(time);
        if (held(time)) {
            back += (float) Math.max(0, RideClient.progress(partialTick) - entryProgress - entrySpeed * coast(time - enteredAt));
        }
        return kept(time) * back;
    }

    /** How far the ride camera moves from its usual place, in blocks. */
    public static Vec3 offset(float partialTick) {
        if (!pulling || !RideClient.isActive()) return Vec3.ZERO;
        float time = RideClient.time(partialTick), back = heldBack(partialTick);
        float kept = kept(time), down = kept * sunk(time);
        if (!held(time)) return new Vec3(0, -down, -back);
        float since = time - enteredAt;
        return new Vec3(kept * (across(holeX, entryX, entryVelX, since) - RideClient.offX(partialTick)),
                kept * (across(holeY, entryY, entryVelY, since) - RideClient.offY(partialTick)) - down, -back);
    }
}
