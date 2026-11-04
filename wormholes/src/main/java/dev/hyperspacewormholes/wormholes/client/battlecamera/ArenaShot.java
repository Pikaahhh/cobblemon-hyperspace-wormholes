package dev.hyperspacewormholes.wormholes.client.battlecamera;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import dev.hyperspacewormholes.wormholes.zmove.BurnsSkyTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The fixed wide shot of boss fights (the Megalo Tower's Necrozma, Hoopa Unbound and the legendaries it summons), played
 * for the whole battle while any far battler is drawn by a boss entity. Necrozma's shot stands behind the allies, centred
 * on the boss, on the compass direction nearest the trainers' side, just far enough back to hold the far set BattleCamera
 * hands over; it eases out for Light That Burns the Sky's flight and back. Any other boss (wide) gets one shot sized from
 * the STAGE_* box, set once and never moved.
 */
final class ArenaShot {
    private static final double PITCH = 15, FOV = 42, FILL = 0.88, TOP = 0.86, BOTTOM = -0.5;
    private static final double WINGSPAN = 2.4, DEPTH = 0.35, REACH = 14, WIDE_REACH = 24;
    private static final double WIDE_PITCH = 18, WIDE_FOV = 55, STAGE_ACROSS = 13, STAGE_BEHIND = 10, STAGE_AHEAD = 19, STAGE_HIGH = 15;
    private static final double STILL = 0.04, MOVED = 1.0, LEVEL = 2.0;
    private static final int SETTLE = 10, CHECK_TICKS = 20;
    private static final double WIDEN_TICKS = 10, EASE_OUT_TICKS = 20, EASE_BACK_TICKS = 30;
    private static final String ULTRA = "ultra-fusion", NECROZMA = "necrozma";
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Logger LOG = LoggerFactory.getLogger("horizons_movefx camera");

    private static int still, sinceCheck, calm;
    private static final Set<Integer> IDS = new HashSet<>();
    private static @Nullable Vec3 lastBoss, rest, side;
    private static final List<AABB> KEEP = new ArrayList<>(), WIDE = new ArrayList<>();
    private static @Nullable BattleCamera.Pose base, from, to;
    private static double easeStart, easeTicks = 1;
    private static boolean flight, wide;
    private static final double MOUTH = 0.85;
    private static final double HELD_RADIUS = BurnsSkyTimeline.HELD_SCALE * BurnsSkyTimeline.BALL_RADIUS
            * BurnsSkyTimeline.BALL_OUTER;

    private ArenaShot() {}

    static boolean on() {
        return to != null;
    }

    static boolean necrozma(Entity boss) {
        return boss instanceof PokemonEntity pokemon
                && pokemon.getPokemon().getSpecies().getResourceIdentifier().getPath().equals(NECROZMA);
    }

    static void clear() {
        IDS.clear();
        still = 0;
        sinceCheck = 0;
        calm = 0;
        wide = false;
        lastBoss = null;
        rest = null;
        side = null;
        KEEP.clear();
        WIDE.clear();
        base = null;
        from = null;
        to = null;
        flight = false;
    }

    static void tick(Minecraft mc, List<Entity> far, Collection<Entity> near, boolean burns, @Nullable AABB ball, double now) {
        if (wide && to != null) return;
        if (far.isEmpty()) {
            clear();
            return;
        }
        Set<Integer> ids = new HashSet<>();
        for (Entity entity : far) ids.add(entity.getId());
        if (!ids.equals(IDS)) {
            clear();
            IDS.addAll(ids);
            wide = far.stream().noneMatch(ArenaShot::necrozma);
        }
        Entity boss = far.getFirst();
        Vec3 at = middle(far);
        still = !burns && lastBoss != null && at.distanceTo(lastBoss) < STILL ? still + 1 : 0;
        lastBoss = at;
        if (rest == null || still >= SETTLE && at.distanceTo(rest) > MOVED && Math.abs(at.y - rest.y) < LEVEL) {
            rest = at;
            side = axis(mc, far, near);
            keep(mc, far, near);
            WIDE.clear();
            flight = false;
            base = fit(mc, List.of());
            to = base;
            from = null;
            AABB drawn = drawn(boss, boss.position());
            LOG.info("[battlecam] arena shot of {} (hitbox {} by {}, framed {} by {}, {} far) at {} looking {}", boss.getName().getString(),
                    String.format("%.1f", boss.getBbWidth()), String.format("%.1f", boss.getBbHeight()),
                    String.format("%.1f", Math.max(drawn.getXsize(), drawn.getZsize())), String.format("%.1f", drawn.getYsize()),
                    far.size(), to.pos(), to.look());
            return;
        }
        BattleCamera.Pose shown = pose(now);
        if (shown == null || base == null) return;
        List<AABB> bodies = new ArrayList<>();
        for (Entity entity : far) bodies.add(drawn(entity, entity.position()));
        if (burns && !flight) {
            flight = true;
            calm = 0;
            WIDE.addAll(flightBoxes(boss));
            WIDE.addAll(bodies);
            ease(shown, fit(mc, WIDE), EASE_OUT_TICKS, now);
            LOG.info("[battlecam] arena shot eased out for Light That Burns the Sky to {}", to.pos());
            return;
        }
        if (!burns) flight = false;
        List<AABB> must = new ArrayList<>(bodies);
        if (ball != null) must.add(ball);
        boolean out = false;
        for (AABB box : must) out |= !inFrame(mc, to, box);
        if (out) {
            calm = 0;
            WIDE.addAll(must);
            ease(shown, fit(mc, WIDE), WIDEN_TICKS, now);
            LOG.info("[battlecam] arena shot widened to keep {} in view: {}", boss.getName().getString(), to.pos());
            return;
        }
        if (!WIDE.isEmpty() && !burns) {
            calm = still > 0 && allInFrame(mc, base, bodies) ? calm + 1 : 0;
            if (calm >= SETTLE) {
                WIDE.clear();
                calm = 0;
                ease(shown, base, EASE_BACK_TICKS, now);
                LOG.info("[battlecam] arena shot back to {}", to.pos());
            }
            return;
        }
        if (burns || ++sinceCheck < CHECK_TICKS) return;
        sinceCheck = 0;
        for (Entity entity : near) {
            if (!onStage(entity) || inFrame(mc, to, entity.getBoundingBox())) continue;
            KEEP.add(entity.getBoundingBox());
            base = fit(mc, List.of());
            ease(shown, base, EASE_BACK_TICKS, now);
            LOG.info("[battlecam] arena shot widened for {} to {}", entity.getName().getString(), to.pos());
            return;
        }
    }

    private static Vec3 middle(List<Entity> far) {
        if (far.size() == 1) return far.getFirst().position();
        Vec3 sum = Vec3.ZERO;
        for (Entity entity : far) sum = sum.add(entity.position());
        return sum.scale(1.0 / far.size());
    }

    static @Nullable BattleCamera.Pose pose(double now) {
        BattleCamera.Pose end = to;
        if (end == null) return null;
        BattleCamera.Pose start = from;
        if (start == null) return end;
        double t = Mth.clamp((now - easeStart) / easeTicks, 0, 1);
        if (t >= 1) {
            from = null;
            return end;
        }
        double e = t * t * (3 - 2 * t);
        return new BattleCamera.Pose(start.pos().lerp(end.pos(), e), start.look().lerp(end.look(), e), UP, Mth.lerp(e, start.fov(), end.fov()));
    }

    private static void ease(BattleCamera.Pose shown, BattleCamera.Pose target, double ticks, double now) {
        from = shown;
        to = target;
        easeStart = now;
        easeTicks = ticks;
    }

    private static Vec3 axis(Minecraft mc, List<Entity> far, Collection<Entity> near) {
        Vec3 centre = rest, sum = Vec3.ZERO;
        for (Entity entity : near) sum = sum.add(entity.position().subtract(centre));
        if (mc.level != null) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (far.contains(entity) || near.contains(entity)) continue;
                boolean ally = entity instanceof Player || entity instanceof PokemonEntity pokemon && pokemon.getOwnerUUID() != null;
                if (ally && entity.position().subtract(centre).horizontalDistance() < reach()) sum = sum.add(entity.position().subtract(centre));
            }
        }
        if (sum.horizontalDistanceSqr() < 1e-4 && mc.player != null) sum = mc.player.position().subtract(centre);
        if (wide && sum.horizontalDistanceSqr() >= 1e-4) return new Vec3(sum.x, 0, sum.z).normalize();
        if (Math.abs(sum.x) > Math.abs(sum.z)) return new Vec3(Math.signum(sum.x), 0, 0);
        return new Vec3(0, 0, sum.z == 0 ? -1 : Math.signum(sum.z));
    }

    private static void keep(Minecraft mc, List<Entity> far, Collection<Entity> near) {
        KEEP.clear();
        if (wide) {
            Vec3 across = new Vec3(-side.z, 0, side.x).scale(STAGE_ACROSS);
            KEEP.add(new AABB(rest.subtract(across).subtract(side.scale(STAGE_BEHIND)),
                    rest.add(across).add(side.scale(STAGE_AHEAD)).add(0, STAGE_HIGH, 0)));
            return;
        }
        for (Entity entity : far) KEEP.add(drawn(entity, entity.position()));
        for (Entity entity : near) if (onStage(entity)) KEEP.add(entity.getBoundingBox());
        if (mc.level == null) return;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof PokemonEntity pokemon) || far.contains(entity) || pokemon.getOwnerUUID() == null) continue;
            if (onStage(entity) && entity.position().subtract(rest).dot(side) > 1) KEEP.add(entity.getBoundingBox());
        }
    }

    private static double reach() {
        return wide ? WIDE_REACH : REACH;
    }

    private static boolean onStage(Entity entity) {
        return rest != null && entity.position().subtract(rest).horizontalDistance() < reach();
    }

    private static AABB drawn(Entity boss, Vec3 at) {
        double height = boss.getBbHeight(), width = boss.getBbWidth();
        if (boss instanceof PokemonEntity pokemon) {
            FormData own = pokemon.getExposedForm();
            EntityDimensions hitbox = own.getHitbox();
            for (FormData form : pokemon.getPokemon().getSpecies().getForms()) {
                if (!form.getAspects().contains(ULTRA) || hitbox.height() <= 0 || own.getBaseScale() <= 0) continue;
                double scale = boss.getBbHeight() / (hitbox.height() * own.getBaseScale());
                height = Math.max(height, form.getHitbox().height() * form.getBaseScale() * scale);
                width = Math.max(width, form.getHitbox().width() * form.getBaseScale() * scale);
            }
        }
        boolean wings = necrozma(boss);
        double across = wings ? Math.max(width, height * WINGSPAN) / 2 : width / 2;
        double deep = wings ? Math.max(width / 2, height * DEPTH) : width / 2;
        boolean alongX = side != null && side.x != 0;
        double dx = alongX ? deep : across, dz = alongX ? across : deep;
        return new AABB(at.x - dx, at.y, at.z - dz, at.x + dx, at.y + height, at.z + dz);
    }

    private static List<AABB> flightBoxes(Entity boss) {
        List<AABB> boxes = new ArrayList<>();
        AABB home = drawn(boss, rest);
        AABB away = home.move(side.scale(-BurnsSkyTimeline.RETREAT_BACK).add(0, home.getYsize() * BurnsSkyTimeline.RISE_BODIES, 0));
        boxes.add(home);
        boxes.add(away);
        for (AABB body : List.of(home, away)) {
            double reach = BurnsSkyTimeline.HEAD_GAP + HELD_RADIUS;
            Vec3 mouth = new Vec3(body.getCenter().x, body.minY + body.getYsize() * MOUTH, body.getCenter().z);
            double angle = BurnsSkyTimeline.HOLD_ANGLE;
            Vec3 centre = mouth.add(side.scale(Math.cos(angle) * reach)).add(0, Math.sin(angle) * reach, 0);
            boxes.add(new AABB(centre, centre).inflate(HELD_RADIUS));
        }
        return boxes;
    }

    private static BattleCamera.Pose fit(Minecraft mc, List<AABB> extra) {
        List<AABB> boxes = new ArrayList<>(KEEP);
        boxes.addAll(extra);
        AABB all = boxes.getFirst();
        for (AABB box : boxes) all = all.minmax(box);
        double pitch = Math.toRadians(wide ? WIDE_PITCH : PITCH), fov = wide ? WIDE_FOV : FOV;
        Vec3 forward = side.scale(-Math.cos(pitch)).add(0, -Math.sin(pitch), 0).normalize();
        Vec3 right = forward.cross(UP).normalize(), up = right.cross(forward);
        double tall = Math.tan(Math.toRadians(fov / 2));
        double across = tall * FILL * mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        Vec3 centre = all.getCenter();
        List<Vec3> corners = new ArrayList<>();
        for (AABB box : boxes) {
            for (int corner = 0; corner < 8; corner++) {
                corners.add(new Vec3((corner & 1) == 0 ? box.minX : box.maxX, (corner & 2) == 0 ? box.minY : box.maxY,
                        (corner & 4) == 0 ? box.minZ : box.maxZ).subtract(centre));
            }
        }
        double low = 0, high = 8 * REACH + 128;
        for (int step = 0; step < 40; step++) {
            double mid = (low + high) / 2;
            if (lift(corners, forward, right, up, mid, tall, across) != null) high = mid;
            else low = mid;
        }
        double[] lift = lift(corners, forward, right, up, high, tall, across);
        double raise = lift == null ? 0 : (lift[0] + lift[1]) / 2;
        Vec3 pos = centre.subtract(forward.scale(high)).add(up.scale(raise));
        return new BattleCamera.Pose(pos, pos.add(forward.scale(high)), UP, fov);
    }

    private static @Nullable double[] lift(List<Vec3> corners, Vec3 forward, Vec3 right, Vec3 up, double distance, double tall,
                                            double wide) {
        double from = -Double.MAX_VALUE, to = Double.MAX_VALUE;
        for (Vec3 p : corners) {
            double depth = p.dot(forward) + distance, y = p.dot(up);
            if (depth <= 0.5 || Math.abs(p.dot(right)) > depth * wide) return null;
            from = Math.max(from, y - depth * tall * TOP);
            to = Math.min(to, y - depth * tall * BOTTOM);
        }
        return from <= to ? new double[]{from, to} : null;
    }

    private static boolean allInFrame(Minecraft mc, BattleCamera.Pose pose, List<AABB> boxes) {
        for (AABB box : boxes) if (!inFrame(mc, pose, box)) return false;
        return true;
    }

    private static boolean inFrame(Minecraft mc, BattleCamera.Pose pose, AABB box) {
        Vec3 forward = pose.look().subtract(pose.pos()).normalize();
        Vec3 right = forward.cross(UP).normalize(), up = right.cross(forward);
        double tall = Math.tan(Math.toRadians(pose.fov() / 2));
        double wide = tall * mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        for (int corner = 0; corner < 8; corner++) {
            Vec3 p = new Vec3((corner & 1) == 0 ? box.minX : box.maxX, (corner & 2) == 0 ? box.minY : box.maxY,
                    (corner & 4) == 0 ? box.minZ : box.maxZ).subtract(pose.pos());
            double depth = p.dot(forward);
            if (depth <= 0 || Math.abs(p.dot(right)) > depth * wide || Math.abs(p.dot(up)) > depth * tall) return false;
        }
        return true;
    }
}
