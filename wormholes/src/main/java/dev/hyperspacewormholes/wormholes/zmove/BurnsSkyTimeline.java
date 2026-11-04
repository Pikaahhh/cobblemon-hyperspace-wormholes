package dev.hyperspacewormholes.wormholes.zmove;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Light That Burns the Sky's timeline: the ball's size and path and Necrozma's retreat, shared by the server
 * (LightThatBurnsTheSky) and the client (BurnsSkyClient, BurnsSkyEffects). Times are Ultra Sun's own battle sequence
 * (30 fps) as at(game frame), starting after the Z-power activation since Mega Showdown shows its own; PACE squeezes
 * the whole timeline. The ball is held in Necrozma's mouth while it forms, eased out forward as it grows, then thrown at the target.
 */
public final class BurnsSkyTimeline {
    public static final float PACE = 1f;
    public static final int START = 100;
    public static final double UNIT = 0.005;
    public static final double BALL_RADIUS = 133 * UNIT;
    public static final double BALL_HALF = 0.95;
    public static final double BALL_OUTER = 1.08;
    public static final double SCALE_3 = 1.5;
    public static final double SCALE_5 = 2.5;
    public static final double SCALE_12 = 4.5;
    public static final double HELD_SCALE = 8;
    public static final double SWELL = 10.5;
    public static final double HEAD_GAP = 0.6;
    public static final double HOLD_ANGLE = 55 * Math.PI / 180;
    public static final double THROW_ARC = 1.5;
    public static final double THROW_START = 0.35;
    public static final double RETREAT_BACK = 7;
    public static final double RISE_BODIES = 0.35;

    public static final int CHARGE = at(START);
    public static final int LIGHT_BORN = at(110);
    public static final int STRIPES = at(120);
    public static final int BALL = at(141);
    public static final int BALL_SMALL = at(186);
    public static final int RETREAT_FROM = at(175);
    public static final int RETREAT_RISE_FROM = RETREAT_FROM;
    public static final int RETREAT_TO = at(271); // back before the ball leaves the mouth
    public static final int HELICES = at(241);
    public static final int HELICES_FADE_IN = at(291);
    public static final int GROW_3_FROM = at(271);
    public static final int GROW_3_TO = at(373);
    public static final int VORTEX_END = at(374);
    public static final int GROW_5 = at(443);
    public static final int GROW_12_FROM = at(462);
    public static final int GROW_12_TO = at(467);
    public static final int GROW_20_FROM = at(495);
    public static final int GROW_20_TO = at(500);
    public static final int[] RINGS = {at(443), at(488)};
    public static final int[] RAYS = {at(457), at(490)};
    public static final int THROW = at(538);
    public static final int THROWN = at(602);
    public static final int LINES_UP_FROM = at(578);
    public static final int LINES_UP_TO = at(603);
    public static final int HIT = at(666);
    public static final int SWELL_FROM = at(684);
    public static final int SWELL_TO = at(689);
    public static final int BALL_GONE = at(697);
    public static final int CRACKS = at(686);
    public static final int CRACKS_END = at(746);
    public static final int DOME = at(696);
    public static final int DOME_FULL = at(710);
    public static final int DOME_SHRINK_FROM = at(720);
    public static final int DOME_SHRINK_TO = at(725);
    public static final int DOME_END = at(746);
    public static final int PRE_GLOW = at(711);
    public static final int PRE_GLOW_END = at(764);
    public static final int FIREBALL = at(712);
    public static final int PILLAR = at(731);
    public static final int PILLAR_RISEN = at(758);
    public static final int PILLAR_GROW_FROM = at(760);
    public static final int SMOKE = at(732);
    public static final int WAVE = at(748);
    public static final int[] WAVE_KEYS = {at(748), at(762), at(794), at(882)};
    public static final float[] WAVE_SCALES = {0.2f, 0.7f, 1.2f, 1.8f};
    public static final int WARM = at(785);
    public static final int SPARKS = at(838);
    public static final int EFFECTS_END = at(883);
    public static final int END = at(896);
    public static final int RETURN_FROM = END;
    public static final int RETURN_TO = END + at(START + 120);

    public static final int[][] ZOOM_LINES = {{at(135), at(155)}, {at(629), at(665)}, {at(721), at(768)}};
    public static final int[][] FILLS = {
            {at(675), at(680), at(681), at(689), 220, 255, 128, 100},
            {at(705), at(712), at(717), at(728), 255, 255, 255, 100},
            {at(868), at(875), at(882), at(896), 255, 255, 255, 100}};
    public static final float[][] SHAKES = {
            {at(667), at(685), 10, 10, 4},
            {at(682), at(710), 10, 2, 8},
            {at(727), at(745), 20, 20, 5},
            {at(750), at(880), 5, 8, 25}};

    private BurnsSkyTimeline() {}

    public static int at(float frame) {
        return Math.round((frame - START) * 2f / 3f * PACE);
    }

    public static double ballScale(double age) {
        if (age < BALL) return 0;
        if (age < BALL_SMALL) return lerp(age, BALL, BALL_SMALL, 0.05, 0.5);
        if (age < GROW_3_FROM) return 0.5;
        if (age < GROW_3_TO) return lerp(age, GROW_3_FROM, GROW_3_TO, 0.5, SCALE_3);
        if (age < GROW_5) return SCALE_3;
        if (age < GROW_12_FROM) return lerp(age, GROW_5, GROW_5 + 3, SCALE_3, SCALE_5);
        if (age < GROW_20_FROM) return lerp(age, GROW_12_FROM, GROW_12_TO, SCALE_5, SCALE_12);
        if (age < SWELL_FROM) return lerp(age, GROW_20_FROM, GROW_20_TO, SCALE_12, HELD_SCALE);
        if (age < BALL_GONE) return lerp(age, SWELL_FROM, SWELL_TO, HELD_SCALE, SWELL);
        return 0;
    }

    public static double ballRadius(double age) {
        return ballScale(age) * BALL_RADIUS;
    }

    public static Vec3 holdDirection(Vec3 dir) {
        return dir.scale(Math.cos(HOLD_ANGLE)).add(0, Math.sin(HOLD_ANGLE), 0);
    }

    public static double outOfMouth(double age) {
        return smooth((age - GROW_3_FROM) / (GROW_3_TO - GROW_3_FROM));
    }

    public static Vec3 held(double age, Vec3 mouth, Vec3 dir) {
        double half = ballRadius(age) * BALL_OUTER;
        double reach = half * (Math.sin(HOLD_ANGLE) + Math.sqrt(2) * Math.cos(HOLD_ANGLE));
        return mouth.add(holdDirection(dir).scale((reach + HEAD_GAP) * outOfMouth(age)));
    }

    public static Vec3 thrown(double age, Vec3 release, Vec3 targetFeet) {
        double radius = ballRadius(age);
        Vec3 landed = targetFeet.add(0, radius * BALL_OUTER, 0);
        if (age < HIT) {
            double s = Mth.clamp((age - THROW) / (HIT - THROW), 0, 1);
            return release.lerp(landed, s * (THROW_START + (1 - THROW_START) * s)).add(0, THROW_ARC * Math.sin(Math.PI * s), 0);
        }
        double s = Math.min(1, (age - HIT) / (SWELL_TO - HIT));
        return landed.lerp(targetFeet.add(0, radius * 0.3, 0), smooth(s));
    }

    public static double retreatRise(double bodyHeight) {
        return bodyHeight * RISE_BODIES;
    }

    public static Vec3 retreat(double age, Vec3 dir, double back, double rise) {
        double away = smooth((age - RETREAT_FROM) / (RETREAT_TO - RETREAT_FROM));
        double home = 1 - smooth((age - RETURN_FROM) / (RETURN_TO - RETURN_FROM));
        return dir.scale(-back * away * home).add(0, rise * away * home, 0);
    }

    public static double waveScale(double age) {
        if (age < WAVE_KEYS[0]) return 0;
        for (int i = 1; i < WAVE_KEYS.length; i++) {
            if (age < WAVE_KEYS[i]) return lerp(age, WAVE_KEYS[i - 1], WAVE_KEYS[i], WAVE_SCALES[i - 1], WAVE_SCALES[i]);
        }
        return WAVE_SCALES[WAVE_SCALES.length - 1];
    }

    public static double lerp(double age, double from, double to, double a, double b) {
        if (to <= from) return age < from ? a : b;
        return Mth.lerp(Mth.clamp((age - from) / (to - from), 0, 1), a, b);
    }

    public static double smooth(double t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
