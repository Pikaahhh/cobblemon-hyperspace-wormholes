package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;

/**
 * Shapes the Ultra Beast worlds are carved from (tubes, balls, cones, faceted heads and inlaid gems), built into a
 * block map.
 */
final class Sculpt {
    static void tube(Vec3 a, Vec3 b, double ra, double rb, Function<BlockPos, BlockState> block, Map<BlockPos, BlockState> into) {
        int steps = Math.max(1, Mth.ceil(a.distanceTo(b) * 2));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            ball(a.lerp(b, t), Mth.lerp(t, ra, rb), block, into);
        }
    }

    static void arch(Vec3 a, Vec3 b, double rise, int steps, double end, DoubleUnaryOperator radius,
                     Function<BlockPos, BlockState> block, Map<BlockPos, BlockState> into) {
        Vec3 previous = a;
        for (int i = 1; i <= steps; i++) {
            double t = end * i / steps;
            Vec3 point = a.lerp(b, t).add(0, Math.sin(t * Math.PI) * rise, 0);
            double r = radius.applyAsDouble(t);
            tube(previous, point, r, r, block, into);
            previous = point;
        }
    }

    static void ball(Vec3 c, double r, Function<BlockPos, BlockState> block, Map<BlockPos, BlockState> into) {
        int reach = Mth.ceil(r);
        for (int x = -reach; x <= reach; x++) {
            for (int y = -reach; y <= reach; y++) {
                for (int z = -reach; z <= reach; z++) {
                    BlockPos p = BlockPos.containing(c.x + x, c.y + y, c.z + z);
                    if (Vec3.atCenterOf(p).distanceToSqr(c) <= r * r) into.put(p, block.apply(p));
                }
            }
        }
    }

    static void cone(Vec3 base, double radius, double height, double tip, Function<BlockPos, BlockState> block, Map<BlockPos, BlockState> into) {
        for (int y = 0; y <= Mth.ceil(height); y++) {
            double r = Mth.lerp(y / height, radius, tip);
            int reach = Mth.ceil(r);
            for (int x = -reach; x <= reach; x++) {
                for (int z = -reach; z <= reach; z++) {
                    if (x * x + z * z > r * r + 0.3) continue;
                    BlockPos p = BlockPos.containing(base.x + x, base.y + y, base.z + z);
                    into.put(p, block.apply(p));
                }
            }
        }
    }

    static void facets(Vec3 c, double r, double yaw, Function<BlockPos, BlockState> block, Map<BlockPos, BlockState> into) {
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        int reach = Mth.ceil(r * 1.3);
        for (int x = -reach; x <= reach; x++) {
            for (int y = -reach; y <= reach; y++) {
                for (int z = -reach; z <= reach; z++) {
                    double rx = x * cos - z * sin, rz = x * sin + z * cos, ry = y * 1.15;
                    if (Math.max(Math.abs(rx), Math.max(Math.abs(ry), Math.abs(rz))) > r) continue;
                    if (Math.abs(rx) + Math.abs(ry) + Math.abs(rz) > r * 1.6) continue;
                    if (Math.abs(rx) + Math.abs(rz) > r * 1.3) continue;
                    BlockPos p = BlockPos.containing(c.x + x, c.y + y, c.z + z);
                    into.put(p, block.apply(p));
                }
            }
        }
    }

    static void inlay(Map<BlockPos, BlockState> shape, int count, double size, BlockState gem, RandomSource random) {
        List<BlockPos> surface = new ArrayList<>();
        for (BlockPos p : shape.keySet()) {
            for (Direction d : Direction.values()) {
                if (d != Direction.DOWN && !shape.containsKey(p.relative(d))) {
                    surface.add(p);
                    break;
                }
            }
        }
        if (surface.isEmpty()) return;
        surface.sort(BlockPos::compareTo);
        for (int i = 0; i < count; i++) {
            BlockPos seed = surface.get(random.nextInt(surface.size()));
            double r = size * (0.7 + random.nextDouble() * 0.6);
            for (BlockPos p : surface) {
                if (p.distSqr(seed) <= r * r) shape.put(p, gem);
            }
        }
    }
}
