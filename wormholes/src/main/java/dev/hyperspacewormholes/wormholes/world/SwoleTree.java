package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Predicate;

/**
 * Buzzwole's swole trees: a jacked torso on a segmented trunk with two flexing arms and palm fronds on top. Built by
 * command or by the Ultra Jungle feature.
 */
public final class SwoleTree {
    private static final int MIN_HEIGHT = 80;
    private static final int MAX_HEIGHT = 110;
    private static final double WAIST = 0.5;
    private static final double BAND = 0.1;
    private static final double[][] LOWER = {{0.00, 1.20}, {0.15, 0.95}, {0.60, 0.85}, {1.00, 0.88}};
    private static final double[][] TORSO_WIDTH = {{0.00, 0.95}, {0.35, 1.45}, {0.70, 2.15}, {1.00, 2.30}};
    private static final double[][] TORSO_DEPTH = {{0.00, 0.85}, {0.40, 1.00}, {0.75, 1.40}, {1.00, 1.25}};

    private static final BlockState TAN = Blocks.STRIPPED_JUNGLE_WOOD.defaultBlockState();
    private static final BlockState DARK = Blocks.JUNGLE_WOOD.defaultBlockState();
    private static final BlockState LEAVES = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

    private enum Pose { FLEX, REACH, SIDE_CHEST }

    public record Shape(Map<BlockPos, BlockState> wood, List<BlockPos> leaves, int height) {
        public Map<Long, List<Map.Entry<BlockPos, BlockState>>> byChunk() {
            Map<Long, List<Map.Entry<BlockPos, BlockState>>> chunks = new HashMap<>();
            for (Map.Entry<BlockPos, BlockState> w : wood.entrySet()) {
                chunks.computeIfAbsent(ChunkPos.asLong(w.getKey()), c -> new ArrayList<>()).add(w);
            }
            for (BlockPos p : leaves) {
                if (wood.containsKey(p)) continue;
                chunks.computeIfAbsent(ChunkPos.asLong(p), c -> new ArrayList<>()).add(Map.entry(p, LEAVES));
            }
            return chunks;
        }
    }

    public static boolean isWood(BlockState state) {
        return state.is(TAN.getBlock()) || state.is(DARK.getBlock());
    }

    public static int build(ServerLevel level, BlockPos base, RandomSource random) {
        Shape shape = shape(base, random);
        shape.wood().forEach((p, state) -> setWood(level, p, state));
        for (BlockPos p : shape.leaves()) {
            if (!shape.wood().containsKey(p)) leaf(level, p);
        }
        hangingVines(level, shape, p -> true);
        return shape.height();
    }

    public static void buildSlice(LevelAccessor level, Shape shape, List<Map.Entry<BlockPos, BlockState>> blocks, long chunk) {
        for (Map.Entry<BlockPos, BlockState> b : blocks) {
            if (b.getValue() == LEAVES) leaf(level, b.getKey());
            else setWood(level, b.getKey(), b.getValue());
        }
        hangingVines(level, shape, p -> ChunkPos.asLong(p) == chunk);
    }

    public static Shape shape(BlockPos base, RandomSource random) {
        int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
        double trunkRadius = 7 + random.nextDouble() * 1.5;
        Map<BlockPos, BlockState> wood = new HashMap<>();
        List<BlockPos> leaves = new ArrayList<>();
        Vec3 foot = Vec3.atBottomCenterOf(base);

        double turn = random.nextDouble() * Math.PI * 2;
        Vec3 across = new Vec3(Math.cos(turn), 0, Math.sin(turn));
        Vec3 front = new Vec3(-across.z, 0, across.x);

        Vec3 top = foot.add(0, height, 0);
        Vec3 waist = foot.add(0, height * WAIST, 0);
        double torsoTall = height * (1 - WAIST);
        segmented(foot.add(0, -6, 0), waist, t -> trunkRadius * smooth(LOWER, t), 13 + random.nextInt(5), wood);
        torso(waist, torsoTall, across, front, trunkRadius, wood);
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2 + random.nextDouble() * 0.6;
            Vec3 out = foot.add(Math.cos(angle) * (trunkRadius + 3), -2, Math.sin(angle) * (trunkRadius + 3));
            tube(foot.add(0, 8, 0), out, trunkRadius * 0.55, trunkRadius * 0.35, TAN, wood);
        }

        Pose pose = Pose.values()[random.nextInt(Pose.values().length)];
        int second = random.nextInt(2);
        Vec3 clasp = top.add(0, -torsoTall * 0.45, 0)
                .add(front.scale(trunkRadius * smooth(TORSO_DEPTH, 0.55) + 6));
        for (int i = 0; i < 2; i++) {
            Vec3 dir = i == 0 ? across : across.reverse();
            Pose arm = pose == Pose.SIDE_CHEST || i == second ? pose : Pose.FLEX;
            double armRadius = 6 + random.nextDouble() * 2;
            Vec3 shoulder = top.add(0, -armRadius, 0);
            double trunkHere = trunkRadius * smooth(TORSO_WIDTH, 1 - armRadius / torsoTall);
            double upper = trunkHere + 15 + random.nextDouble() * 8;
            double fore = 16 + random.nextDouble() * 8;
            Vec3 elbow, wrist;
            if (arm == Pose.REACH) {
                double angle = Math.toRadians(15 + random.nextDouble() * 10);
                Vec3 out = dir.scale(Math.cos(angle)).add(0, Math.sin(angle), 0);
                elbow = shoulder.add(out.scale(upper));
                wrist = elbow.add(out.scale(fore));
            } else if (arm == Pose.SIDE_CHEST) {
                elbow = shoulder.add(dir.scale(trunkHere + armRadius * 1.2)).add(0, -torsoTall * 0.45, 0).add(front.scale(armRadius));
                wrist = clasp.add(dir.scale(armRadius * 0.9));
                upper = elbow.distanceTo(shoulder);
                fore = wrist.distanceTo(elbow);
            } else {
                elbow = shoulder.add(dir.scale(upper)).add(0, 2, 0);
                wrist = elbow.add(0, fore, 0).subtract(dir.scale(fore * 0.3));
            }

            ball(shoulder.add(dir.scale(trunkHere + armRadius * 0.3)), armRadius * 1.3, TAN, wood);
            Vec3 armStart = shoulder.add(dir.scale(trunkHere * 0.7));
            segmented(armStart, elbow, f -> armRadius * Mth.lerp(f, 1.0, 0.9), upper, wood);
            Vec3 upperDir = elbow.subtract(armStart).normalize();
            Vec3 bulge = arm == Pose.SIDE_CHEST ? front : new Vec3(0, 1, 0).subtract(upperDir.scale(upperDir.y)).normalize();
            double upperLength = elbow.distanceTo(armStart);
            stretched(armStart.lerp(elbow, 0.55).add(bulge.scale(armRadius * 0.7)), upperDir, upperLength * 0.33, armRadius * 1.3, TAN, wood);
            stretched(armStart.lerp(elbow, 0.42).add(bulge.scale(-armRadius * 0.45)), upperDir, upperLength * 0.3, armRadius, TAN, wood);
            ball(elbow.add(upperDir.scale(armRadius * 0.5)).add(bulge.scale(-armRadius * 0.2)), armRadius * 0.95, DARK, wood);
            segmented(elbow, wrist, f -> armRadius * Mth.lerp(f, 1.15, 0.8), fore * 0.55, wood);
            Vec3 forearm = wrist.subtract(elbow).normalize();
            Vec3 swell = arm == Pose.REACH ? bulge : arm == Pose.SIDE_CHEST ? new Vec3(0, -1, 0) : dir;
            stretched(elbow.lerp(wrist, 0.3).add(swell.scale(armRadius * 0.45)), forearm, fore * 0.28, armRadius * 1.15, TAN, wood);
            double stump = armRadius * 1.2;
            knob(wrist, forearm, stump, wood);
            if (arm != Pose.SIDE_CHEST) tuft(wrist.add(forearm.scale(stump)).add(0, stump, 0), 7 + random.nextInt(3), 10, random, leaves);
            else if (i == 0) tuft(clasp.add(0, stump, 0), 7 + random.nextInt(3), 10, random, leaves);
        }

        return new Shape(wood, leaves, height);
    }

    private static double smooth(double[][] table, double t) {
        for (int i = 1; i < table.length; i++) {
            if (t <= table[i][0]) {
                double f = (t - table[i - 1][0]) / (table[i][0] - table[i - 1][0]);
                f = f * f * (3 - 2 * f);
                return Mth.lerp(f, table[i - 1][1], table[i][1]);
            }
        }
        return table[table.length - 1][1];
    }

    private static void torso(Vec3 waist, double tall, Vec3 across, Vec3 front, double radius, Map<BlockPos, BlockState> into) {
        double maxW = radius * 2.4;
        for (int y = Mth.floor(waist.y); y < Mth.floor(waist.y + tall); y++) {
            double u = (y + 0.5 - waist.y) / tall;
            double w = radius * smooth(TORSO_WIDTH, u), d = radius * smooth(TORSO_DEPTH, u);
            for (int x = Mth.floor(waist.x - maxW); x <= Mth.ceil(waist.x + maxW); x++) {
                for (int z = Mth.floor(waist.z - maxW); z <= Mth.ceil(waist.z + maxW); z++) {
                    Vec3 off = new Vec3(x + 0.5 - waist.x, 0, z + 0.5 - waist.z);
                    double a = off.dot(across) / w, b = off.dot(front) / d;
                    if (a * a + b * b > 1) continue;
                    into.put(new BlockPos(x, y, z), y < waist.y + 2 ? DARK : TAN);
                }
            }
        }
        double pecU = 0.8;
        double pecW = radius * smooth(TORSO_WIDTH, pecU), pecD = radius * smooth(TORSO_DEPTH, pecU);
        for (int side = -1; side <= 1; side += 2) {
            Vec3 c = waist.add(0, tall * pecU, 0).add(across.scale(side * pecW * 0.45)).add(front.scale(pecD * 0.55));
            muscle(c, across, front, pecW * 0.45, tall * 0.13, pecD * 0.5, into);
        }
        for (double u : new double[]{0.2, 0.36, 0.52}) {
            double w = radius * smooth(TORSO_WIDTH, u), d = radius * smooth(TORSO_DEPTH, u);
            for (int side = -1; side <= 1; side += 2) {
                Vec3 c = waist.add(0, tall * u, 0).add(across.scale(side * w * 0.21)).add(front.scale(d * 0.85));
                muscle(c, across, front, w * 0.19, tall * 0.075, d * 0.32, into);
            }
        }
        double latU = 0.62;
        double latW = radius * smooth(TORSO_WIDTH, latU), latD = radius * smooth(TORSO_DEPTH, latU);
        for (int side = -1; side <= 1; side += 2) {
            Vec3 c = waist.add(0, tall * latU, 0).add(across.scale(side * latW * 0.6)).subtract(front.scale(latD * 0.4));
            muscle(c, across, front, latW * 0.42, tall * 0.26, latD * 0.55, into);
        }
        groove(waist.add(0, tall * 0.08, 0), waist.add(0, tall * 0.94, 0), front, radius * 3, into);
        groove(waist.add(0, tall * 0.08, 0), waist.add(0, tall * 0.9, 0), front.reverse(), radius * 3, into);
        Vec3 underPecs = waist.add(0, tall * (pecU - 0.13), 0);
        groove(underPecs.add(across.scale(-pecW * 0.9)), underPecs.add(across.scale(pecW * 0.9)), front, radius * 3, into);
        for (double u : new double[]{0.28, 0.44, 0.6}) {
            double w = radius * smooth(TORSO_WIDTH, u);
            Vec3 row = waist.add(0, tall * u, 0);
            groove(row.add(across.scale(-w * 0.42)), row.add(across.scale(w * 0.42)), front, radius * 3, into);
        }
    }

    private static void muscle(Vec3 c, Vec3 across, Vec3 front, double wide, double tall, double deep,
                               Map<BlockPos, BlockState> into) {
        double r = Math.max(wide, Math.max(tall, deep));
        for (int x = Mth.floor(c.x - r); x <= Mth.ceil(c.x + r); x++) {
            for (int y = Mth.floor(c.y - r); y <= Mth.ceil(c.y + r); y++) {
                for (int z = Mth.floor(c.z - r); z <= Mth.ceil(c.z + r); z++) {
                    Vec3 d = new Vec3(x + 0.5 - c.x, y + 0.5 - c.y, z + 0.5 - c.z);
                    double a = d.dot(across) / wide, b = d.y / tall, f = d.dot(front) / deep;
                    if (a * a + b * b + f * f <= 1) into.putIfAbsent(new BlockPos(x, y, z), TAN);
                }
            }
        }
    }

    private static void groove(Vec3 a, Vec3 b, Vec3 outward, double reach, Map<BlockPos, BlockState> into) {
        double length = a.distanceTo(b);
        for (double s = 0; s <= length; s += 0.5) {
            Vec3 p = a.lerp(b, length == 0 ? 0 : s / length);
            for (double out = reach; out >= 0; out -= 0.5) {
                BlockPos pos = BlockPos.containing(p.add(outward.scale(out)));
                if (into.containsKey(pos)) {
                    into.put(pos, DARK);
                    break;
                }
            }
        }
    }

    private static void segmented(Vec3 a, Vec3 b, DoubleUnaryOperator radius, double segment, Map<BlockPos, BlockState> into) {
        Vec3 ab = b.subtract(a);
        double length = ab.length();
        double each = length / Math.max(1, (int) Math.round(length / segment));
        double r = Math.max(radius.applyAsDouble(0), Math.max(radius.applyAsDouble(0.5), radius.applyAsDouble(1))) * 1.35;
        for (int x = Mth.floor(Math.min(a.x, b.x) - r); x <= Mth.ceil(Math.max(a.x, b.x) + r); x++) {
            for (int y = Mth.floor(Math.min(a.y, b.y) - r); y <= Mth.ceil(Math.max(a.y, b.y) + r); y++) {
                for (int z = Mth.floor(Math.min(a.z, b.z) - r); z <= Mth.ceil(Math.max(a.z, b.z) + r); z++) {
                    Vec3 p = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    double along = p.subtract(a).dot(ab) / length;
                    if (along < 0 || along > length) continue;
                    double s = (along % each) / each;
                    boolean band = s < BAND || s > 1 - BAND;
                    double here = radius.applyAsDouble(along / length);
                    double rad = band ? here * 0.86 : here * (0.9 + 0.22 * Math.sin(Math.PI * s));
                    if (p.distanceToSqr(a.add(ab.scale(along / length))) > rad * rad) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (band) into.put(pos, DARK);
                    else into.putIfAbsent(pos, TAN);
                }
            }
        }
    }

    private static void tube(Vec3 a, Vec3 b, double ra, double rb, BlockState state, Map<BlockPos, BlockState> into) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double r = Math.max(ra, rb);
        for (int x = Mth.floor(Math.min(a.x, b.x) - r); x <= Mth.ceil(Math.max(a.x, b.x) + r); x++) {
            for (int y = Mth.floor(Math.min(a.y, b.y) - r); y <= Mth.ceil(Math.max(a.y, b.y) + r); y++) {
                for (int z = Mth.floor(Math.min(a.z, b.z) - r); z <= Mth.ceil(Math.max(a.z, b.z) + r); z++) {
                    Vec3 p = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    double t = len2 == 0 ? 0 : Mth.clamp(p.subtract(a).dot(ab) / len2, 0, 1);
                    double rad = Mth.lerp(t, ra, rb);
                    if (p.distanceToSqr(a.add(ab.scale(t))) <= rad * rad) into.putIfAbsent(new BlockPos(x, y, z), state);
                }
            }
        }
    }

    private static void ball(Vec3 c, double r, BlockState state, Map<BlockPos, BlockState> into) {
        for (int x = Mth.floor(c.x - r); x <= Mth.ceil(c.x + r); x++) {
            for (int y = Mth.floor(c.y - r); y <= Mth.ceil(c.y + r); y++) {
                for (int z = Mth.floor(c.z - r); z <= Mth.ceil(c.z + r); z++) {
                    double dx = x + 0.5 - c.x, dy = y + 0.5 - c.y, dz = z + 0.5 - c.z;
                    if (dx * dx + dy * dy + dz * dz <= r * r) into.putIfAbsent(new BlockPos(x, y, z), state);
                }
            }
        }
    }

    private static void stretched(Vec3 c, Vec3 dir, double along, double across, BlockState state, Map<BlockPos, BlockState> into) {
        int r = Mth.ceil(Math.max(along, across));
        for (int x = Mth.floor(c.x) - r; x <= Mth.floor(c.x) + r; x++) {
            for (int y = Mth.floor(c.y) - r; y <= Mth.floor(c.y) + r; y++) {
                for (int z = Mth.floor(c.z) - r; z <= Mth.floor(c.z) + r; z++) {
                    Vec3 d = new Vec3(x + 0.5 - c.x, y + 0.5 - c.y, z + 0.5 - c.z);
                    double a = d.dot(dir);
                    double b2 = d.lengthSqr() - a * a;
                    if (a * a / (along * along) + b2 / (across * across) <= 1) into.putIfAbsent(new BlockPos(x, y, z), state);
                }
            }
        }
    }

    private static void knob(Vec3 wrist, Vec3 along, double r, Map<BlockPos, BlockState> into) {
        Vec3 c = wrist.add(along.scale(r));
        int reach = Mth.ceil(r * 1.3) + 1;
        for (int x = Mth.floor(c.x) - reach; x <= Mth.floor(c.x) + reach; x++) {
            for (int y = Mth.floor(c.y) - reach; y <= Mth.floor(c.y) + reach; y++) {
                for (int z = Mth.floor(c.z) - reach; z <= Mth.floor(c.z) + reach; z++) {
                    Vec3 d = new Vec3(x + 0.5, y + 0.5, z + 0.5).subtract(c);
                    double a = d.dot(along), side2 = d.lengthSqr() - a * a;
                    if (a * a / (r * r * 1.1) + side2 / (r * r * 1.2) > 1) continue;
                    into.put(new BlockPos(x, y, z), a < -r * 0.7 ? DARK : TAN);
                }
            }
        }
    }

    private static void tuft(Vec3 c, int fronds, int length, RandomSource random, List<BlockPos> into) {
        double turn = random.nextDouble() * Math.PI * 2;
        for (int f = 0; f < fronds; f++) {
            double angle = turn + f * Math.PI * 2 / fronds + (random.nextDouble() - 0.5) * 0.4;
            double lift = 0.9 + random.nextDouble() * 0.5;
            Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            for (double i = 0; i <= length; i += 0.5) {
                double t = i / length;
                Vec3 p = c.add(dir.scale(i)).add(0, lift * i * (1 - t * 1.6), 0);
                into.add(BlockPos.containing(p));
                if (t > 0.15 && t < 0.8) {
                    into.add(BlockPos.containing(p.add(side)));
                    into.add(BlockPos.containing(p.subtract(side)));
                }
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (x * x + y * y + z * z <= 5) into.add(BlockPos.containing(c.add(x, y, z)));
                }
            }
        }
    }

    private static void setWood(LevelAccessor level, BlockPos p, BlockState state) {
        if (p.getY() <= level.getMinBuildHeight() || p.getY() >= level.getMaxBuildHeight()) return;
        if (!level.getBlockState(p).is(Blocks.BEDROCK)) level.setBlock(p, state, Block.UPDATE_CLIENTS);
    }

    private static void leaf(LevelAccessor level, BlockPos p) {
        BlockState state = level.getBlockState(p);
        if (state.isAir() || state.canBeReplaced()) level.setBlock(p, LEAVES, Block.UPDATE_CLIENTS);
    }

    private static void hangingVines(LevelAccessor level, Shape shape, Predicate<BlockPos> inside) {
        for (BlockPos leaf : shape.leaves()) {
            long h = mix(leaf.asLong());
            if ((h & 1023) > 61) continue;
            Direction side = Direction.from2DDataValue((int) (h >>> 10 & 3));
            BlockPos v = leaf.relative(side);
            int drop = 2 + (int) (h >>> 12 & 7) % 6;
            for (int i = 0; i < drop && inside.test(v) && level.getBlockState(v).isAir(); i++, v = v.below()) {
                vine(level, v, side);
            }
        }
        for (BlockPos p : shape.wood().keySet()) {
            if ((mix(p.asLong() ^ 0x5EED) & 1023) > 9) continue;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos v = p.relative(side);
                if (inside.test(v) && !shape.wood().containsKey(v) && level.getBlockState(v).isAir()) vine(level, v, side);
            }
        }
    }

    private static void vine(LevelAccessor level, BlockPos pos, Direction side) {
        level.setBlock(pos, Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(side.getOpposite()), true),
                Block.UPDATE_CLIENTS);
    }

    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        return h ^ (h >>> 33);
    }
}
