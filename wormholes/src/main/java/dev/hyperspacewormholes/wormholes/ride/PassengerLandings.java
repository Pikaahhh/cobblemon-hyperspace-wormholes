package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.entity.WormholeEntity;
import dev.hyperspacewormholes.wormholes.world.Zone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where everyone who comes out of a wormhole stands, far enough off that nobody is pushed back in: the driver first,
 * then each passenger, on ground that can be walked to from the arrival (STEP up or down at a time, up to RANGE out),
 * out of any hazard, drop and deeper water than the arrival's, inside the zone's walls and out of MegaloKeepOut. Each
 * stands MOUTH_CLEAR from every wormhole's mouth and in front of the one they came out of, no passenger nearer it than
 * the driver, APART from each other and from anyone already there, off any Pokemon, and no nearer the zone's spot than
 * SPOT_CLEAR (or than the arrival, if that is nearer), so nothing waiting there is set off. Where a place has no such
 * spot the mouth's distance gives way first, then the players', then keeping off a ledge's edge; the last resort is the
 * spot furthest from both. Somewhere with no floor (flying, on water) the same goes at the arrival's height.
 */
final class PassengerLandings {
    private static final int RANGE = 18;
    private static final double MOUTH_CLEAR = 8, APART = 2, SPOT_CLEAR = 18, STEP = 1.01, NEARBY = 40;
    private static final double WIDTH = 0.6, HEIGHT = 1.8;
    private static final double[] MOUTH_STEPS = {MOUTH_CLEAR, 7, 6, 5, 4}, APART_STEPS = {APART, 1.5, 1};
    private static final double MOUTH_WIDE = WormholeEntity.RIM * 0.8;

    private record Cell(int dx, int dz, Vec3 at, boolean edge) {}

    private record Hole(Vec3 centre, @Nullable Vec3 facing, double wide) {
        static Hole of(WormholeEntity wormhole, Vec3 arrival) {
            float yaw = wormhole.getYRot() * Mth.DEG_TO_RAD, pitch = wormhole.getXRot() * Mth.DEG_TO_RAD;
            Vec3 facing = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
            boolean ahead = arrival.add(0, HEIGHT / 2, 0).subtract(wormhole.center()).dot(facing) > 0;
            return new Hole(wormhole.center(), ahead ? facing : null, MOUTH_WIDE * wormhole.getScale());
        }

        boolean behind(Vec3 at) {
            return facing != null && at.add(0, HEIGHT / 2, 0).subtract(centre).dot(facing) <= 0;
        }

        double distance(Vec3 at) {
            Vec3 offset = at.add(0, HEIGHT / 2, 0).subtract(centre);
            if (facing == null) return Math.max(0, offset.length() - wide);
            double along = offset.dot(facing);
            return Math.hypot(along, Math.max(0, offset.subtract(facing.scale(along)).length() - wide));
        }
    }

    private PassengerLandings() {}

    /**
     * Spots for count players: the driver's first. ours are the ones landing, who are no obstacle; exit is the wormhole
     * they come out of, if there is one; hold keeps the driver on the arrival (a legend flying to it).
     */
    static List<Vec3> around(ServerLevel level, Vec3 arrival, float yaw, int count, Collection<ServerPlayer> ours,
                             @Nullable WormholeEntity exit, boolean hold) {
        level.getChunk(BlockPos.containing(arrival));
        Vec3 floor = feet(level, arrival.x, arrival.y, arrival.z);
        Vec3 from = floor != null ? floor : arrival;
        List<Hole> holes = new ArrayList<>();
        for (WormholeEntity wormhole : level.getEntitiesOfClass(WormholeEntity.class, new AABB(from, from).inflate(NEARBY))) {
            if (wormhole != exit && !wormhole.isClosing()) holes.add(Hole.of(wormhole, from));
        }
        if (exit != null) holes.add(Hole.of(exit, from));
        List<Vec3> taken = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.isAlive() && !ours.contains(player)) taken.add(player.position());
        }
        List<Cell> cells = walk(level, arrival, from, floor != null);
        List<Vec3> out = new ArrayList<>();
        Vec3 ahead = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
        for (int i = 0; i < count; i++) {
            Vec3 driver = i == 0 ? null : out.getFirst();
            Vec3 at = hold && i == 0 ? arrival : pick(cells, holes, taken, i == 0 ? from : driver, ahead,
                    driver == null || exit == null ? 0 : mouth(holes, driver) - 0.01);
            out.add(at == null ? from : at);
            taken.add(out.getLast());
        }
        return out;
    }

    private static @Nullable Vec3 pick(List<Cell> cells, List<Hole> holes, List<Vec3> taken, Vec3 near, Vec3 ahead,
                                       double out) {
        for (boolean edges : new boolean[]{false, true}) {
            for (double least : new double[]{out, 0}) {
                for (double apart : APART_STEPS) {
                    for (double clear : MOUTH_STEPS) {
                        Vec3 at = nearest(cells, holes, taken, near, ahead, Math.max(least, clear), apart, edges);
                        if (at != null) return at;
                    }
                }
            }
        }
        Vec3 best = null;
        double most = -2;
        for (Cell cell : cells) {
            double apart = nearest(taken, cell.at), score = Math.min(mouth(holes, cell.at), 4 * apart);
            if (apart > 0 && score > most) {
                most = score;
                best = cell.at;
            }
        }
        return best;
    }

    private static @Nullable Vec3 nearest(List<Cell> cells, List<Hole> holes, List<Vec3> taken, Vec3 near, Vec3 ahead,
                                          double clear, double apart, boolean edges) {
        Vec3 best = null;
        double least = Double.MAX_VALUE;
        for (Cell cell : cells) {
            if (cell.edge && !edges || mouth(holes, cell.at) < clear || nearest(taken, cell.at) < apart) continue;
            Vec3 offset = cell.at.subtract(near);
            double cost = offset.length() + 0.5 * Math.max(0, offset.dot(ahead));
            if (cost < least) {
                least = cost;
                best = cell.at;
            }
        }
        return best;
    }

    private static double mouth(List<Hole> holes, Vec3 at) {
        double least = Double.MAX_VALUE;
        for (Hole hole : holes) least = Math.min(least, hole.behind(at) ? -1 : hole.distance(at));
        return least;
    }

    private static double nearest(List<Vec3> taken, Vec3 at) {
        double least = Double.MAX_VALUE;
        for (Vec3 other : taken) {
            if (Math.abs(other.y - at.y) < 3) least = Math.min(least, Math.hypot(other.x - at.x, other.z - at.z));
        }
        return least;
    }

    /** Every column that can be walked (or, with no floor, flown level) to from the arrival, and may be landed on. */
    private static List<Cell> walk(ServerLevel level, Vec3 arrival, Vec3 from, boolean floor) {
        Zone zone = UltraZones.of(level);
        if (zone != null) zone = zone.near(from.x, from.z);
        boolean walled = zone != null && zone.allows(from.x, from.y, from.z);
        Vec3 spot = zone != null && zone.spot() != null ? zone.standOn(zone.spot()) : null;
        double keep = spot == null ? 0 : Math.min(SPOT_CLEAR, from.distanceTo(spot));
        double depth = depth(level, from);
        Map<Long, Boolean> seen = new HashMap<>();
        ArrayDeque<Cell> open = new ArrayDeque<>();
        List<Cell> out = new ArrayList<>();
        open.add(new Cell(0, 0, from, false));
        seen.put(0L, true);
        while (!open.isEmpty()) {
            Cell cell = open.poll();
            List<Cell> next = new ArrayList<>(4);
            int edges = 0;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                int dx = cell.dx + side.getStepX(), dz = cell.dz + side.getStepZ();
                double x = from.x + dx, z = from.z + dz;
                level.getChunk(BlockPos.containing(x, from.y, z));
                Vec3 flat = new Vec3(x, from.y, z);
                Vec3 at = floor ? feet(level, x, cell.at.y, z) : room(level, flat) ? flat : null;
                if (at == null) {
                    if (floor && room(level, new Vec3(x, cell.at.y, z))) edges++;
                    continue;
                }
                next.add(new Cell(dx, dz, at, false));
            }
            boolean stands = floor || room(level, cell.at);
            if (stands && !onPokemon(level, cell.at) && (spot == null || cell.at.distanceTo(spot) >= keep - 0.01)) {
                out.add(new Cell(cell.dx, cell.dz, cell.at, edges > 0));
            }
            for (Cell side : next) {
                if (side.dx * side.dx + side.dz * side.dz > RANGE * RANGE) continue;
                if (seen.putIfAbsent(((long) side.dx << 32) ^ (side.dz & 0xffffffffL), true) != null) continue;
                if (walled && !zone.allows(side.at.x, side.at.y, side.at.z)) continue;
                if (walled && level.dimension() == Wormholes.ULTRA_MEGALOPOLIS
                        && MegaloKeepOut.inside(zone, side.at)) continue;
                if (hazard(level, side.at) || depth(level, side.at) > depth + 0.05) continue;
                open.add(side);
            }
        }
        return out;
    }

    /** The floor a player at x z comes to rest on within STEP of y, with room to stand, or null. */
    private static @Nullable Vec3 feet(ServerLevel level, double x, double y, double z) {
        for (double up : new double[]{STEP, 0.51, 0.01}) {
            AABB box = box(x, y + up, z);
            if (!level.noBlockCollision(null, box)) continue;
            double fall = up + STEP;
            double down = Shapes.collide(Direction.Axis.Y, box, level.getBlockCollisions(null, box.expandTowards(0, -fall, 0)), -fall);
            return down <= -fall + 1e-4 ? null : new Vec3(x, y + up + down, z);
        }
        return null;
    }

    private static AABB box(double x, double y, double z) {
        return new AABB(x - WIDTH / 2, y, z - WIDTH / 2, x + WIDTH / 2, y + HEIGHT, z + WIDTH / 2);
    }

    private static boolean room(ServerLevel level, Vec3 at) {
        return level.noBlockCollision(null, box(at.x, at.y, at.z));
    }

    private static boolean onPokemon(ServerLevel level, Vec3 at) {
        return !level.getEntitiesOfClass(PokemonEntity.class, box(at.x, at.y, at.z).inflate(1)).isEmpty();
    }

    private static boolean hazard(ServerLevel level, Vec3 at) {
        BlockPos in = BlockPos.containing(at.x, at.y + 0.01, at.z), under = BlockPos.containing(at.x, at.y - 0.01, at.z);
        return level.getFluidState(in).is(FluidTags.LAVA) || WormholeDestinations.isHazard(level.getBlockState(in))
                || WormholeDestinations.isHazard(level.getBlockState(under));
    }

    /** How deep the feet are in water or lava there. */
    private static double depth(ServerLevel level, Vec3 at) {
        double top = at.y;
        BlockPos.MutableBlockPos pos = BlockPos.containing(at.x, at.y + 0.01, at.z).mutable();
        for (int i = 0; i < 3; i++, pos.move(Direction.UP)) {
            FluidState fluid = level.getFluidState(pos);
            if (fluid.isEmpty()) break;
            top = pos.getY() + fluid.getHeight(level, pos);
        }
        return top - at.y;
    }
}
