package dev.hyperspacewormholes.wormholes.ride;

import dev.hyperspacewormholes.wormholes.compat.WaystoneLinks;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The tunnel on a warp stone ride. One wormhole per waystone, as far along as that waystone is away, coloured by its
 * dimension.
 */
public final class WaystoneWormholes {
    private static final double FIRST = 150;
    private static final double MIN_GAP = 60;
    private static final float LATERAL_SPREAD = 11f;

    private WaystoneWormholes() {}

    private static double distance(WaystoneLinks.Target target, Vec3 from) {
        double dx = target.pos().getX() + 0.5 - from.x;
        double dz = target.pos().getZ() + 0.5 - from.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static List<WaystoneLinks.Target> sortByDistance(List<WaystoneLinks.Target> targets, Vec3 from) {
        return targets.stream().sorted(Comparator.comparingDouble(t -> distance(t, from))).toList();
    }

    public static List<WarpTunnel.Wormhole> layout(long seed, List<WaystoneLinks.Target> targets, Vec3 from) {
        List<WarpTunnel.Wormhole> wormholes = new ArrayList<>();
        double last = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < targets.size(); i++) {
            WaystoneLinks.Target target = targets.get(i);
            double at = Math.max(Math.max(FIRST, distance(target, from)), last + MIN_GAP);
            last = at;
            Random random = new Random(seed ^ (i * 0x9E3779B97F4A7C15L));
            double angle = random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(random.nextDouble()) * LATERAL_SPREAD;
            WormholeType type = target.dimension() == Level.NETHER ? WormholeType.RED
                    : target.dimension() == Level.END ? WormholeType.WHITE : WormholeType.BLUE;
            wormholes.add(new WarpTunnel.Wormhole(i, at, (float) (Math.cos(angle) * r), (float) (Math.sin(angle) * r), 0, type));
        }
        return wormholes;
    }
}
