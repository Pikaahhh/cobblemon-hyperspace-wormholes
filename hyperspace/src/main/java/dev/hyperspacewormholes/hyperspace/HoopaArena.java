package dev.hyperspacewormholes.hyperspace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.List;

/**
 * The island the Hoopa Unbound event is fought on: rolling plains hills on every side with copses on top (natural blocks
 * only), and a level floor in the valley out to FLOOR_REACH for Hoopa, the seats and the legendaries. The way home stands
 * HOME blocks out along +z on a rock face where the hill slips away. /hyperspace arena rebuilds it on the island you're on.
 */
public final class HoopaArena {
    private static final int GROUND = 64, FLOOR_REACH = 24, HOME = 30, REACH = 48, DEEP = 20, SCARP = 5, MELT = 13;
    private static final int OAKS = 16, FALLEN_ROCKS = 6, FLOWER_PATCHES = 7;
    private static final long SEED = 63352;
    private static final double RADIUS = 43, GAP = 0.3, PATCH_SPREAD = 2.5;
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState(), DIRT = Blocks.DIRT.defaultBlockState(),
            STONE = Blocks.STONE.defaultBlockState(), ANDESITE = Blocks.ANDESITE.defaultBlockState(), MOSS = Blocks.MOSS_BLOCK.defaultBlockState(),
            LOG = Blocks.OAK_LOG.defaultBlockState(), LEAVES = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    private static final List<BlockState> FLOWERS = List.of(Blocks.DANDELION.defaultBlockState(), Blocks.POPPY.defaultBlockState(),
            Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState());

    private final ServerLevel level;
    private final BlockPos centre;
    private final RandomSource random;
    private final double[] seeds = new double[7];
    private final int[][] tops = new int[REACH * 2 + 1][REACH * 2 + 1];
    private final int floor;

    private interface Column {
        void at(int x, int z);
    }

    private HoopaArena(ServerLevel level, BlockPos centre, RandomSource random) {
        this.level = level;
        this.centre = centre;
        this.random = random;
        for (int i = 0; i < seeds.length; i++) seeds[i] = random.nextDouble() * Mth.TWO_PI;
        floor = (int) Math.round(hill(0, HOME - 2));
    }

    public static void build(ServerLevel level, BlockPos centre, int slot, Zones.Zone zone) {
        HoopaArena arena = new HoopaArena(level, centre, RandomSource.create(SEED + slot));
        arena.ground();
        arena.rockFaces();
        for (int i = 0; i < OAKS; i++) arena.oakAt(arena.angle(), 33 + arena.random.nextDouble() * 8);
        arena.fallenRocks();
        arena.cover(0.6, 0.08);
        arena.flowers();
        arena.wayHome(zone);
        Hyperspace.LOGGER.info("[hoopa] built the arena on island slot {}", slot);
    }

    private double distance(int x, int z) {
        return Math.sqrt(x * x + z * z);
    }

    private double edge(int x, int z) {
        double a = Math.atan2(z, x);
        return RADIUS + 2.2 * Math.sin(3 * a + seeds[4]) + 1.3 * Math.sin(5 * a + seeds[5]);
    }

    private boolean inside(int x, int z) {
        return Math.abs(x) <= REACH && Math.abs(z) <= REACH && distance(x, z) <= edge(x, z);
    }

    // soft rolling noise, about -1 to 1
    private double wave(int x, int z) {
        return (Math.sin(x * 0.19 + seeds[0]) + Math.sin(z * 0.23 + seeds[1]) + Math.sin((x + z) * 0.13 + seeds[2])
                + Math.sin((x - z) * 0.31 + seeds[3])) / 2.5;
    }

    private double angle() {
        return random.nextDouble() * Mth.TWO_PI;
    }

    // behind the seats, where the battle camera stands and looks back over; nothing tall grows here
    private boolean shot(int x, int z) {
        return z > 8 && Math.abs(x) < z * 0.8;
    }

    private void each(Column column) {
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) if (inside(x, z)) column.at(x, z);
        }
    }

    private void set(int x, int y, int z, BlockState state) {
        level.setBlock(centre.offset(x, y - centre.getY(), z), state, QUIET);
    }

    private BlockState get(int x, int y, int z) {
        return level.getBlockState(centre.offset(x, y - centre.getY(), z));
    }

    private int top(int x, int z) {
        return tops[x + REACH][z + REACH];
    }

    private double hill(int x, int z) {
        return GROUND + Math.max(0, distance(x, z) - FLOOR_REACH - 2) * 0.55 * (1 + 0.8 * wave(x, z));
    }

    private int side(int x) {
        return Math.max(0, Math.abs(x) - 2);
    }

    private double face(int x) {
        return HOME + Math.min(side(x) / 3.0, 1) * 2.2 * Math.sin(x * 0.5 + seeds[6]);
    }

    // the hill slips away in front of the rock face and stands proud behind it, fading out to MELT blocks each side
    private double height(int x, int z) {
        if (Math.abs(x) <= 2 && z >= HOME - 3 && z < HOME) return floor;
        double hill = hill(x, z), melt = Math.min(1, side(x) / (double) MELT), stands = 1 - melt * melt * (3 - 2 * melt);
        double out = face(x) - z;
        if (out > 0) return Mth.lerp(stands * (1 - Mth.clamp((out - 2) / 5, 0, 1)), hill, floor);
        double lip = floor + SCARP + (side(x) == 0 ? 0 : wave(x * 3, z * 3)) - out * 0.3;
        return Mth.lerp(stands, hill, Math.max(hill, lip));
    }

    private void ground() {
        each((x, z) -> {
            int top = (int) Math.round(height(x, z));
            tops[x + REACH][z + REACH] = top;
            double d = distance(x, z) / edge(x, z);
            int bottom = Math.min(top, GROUND) - 4 - (int) Math.round(DEEP * Math.sqrt(Math.max(0, 1 - d * d)));
            for (int y = bottom; y <= top; y++) set(x, y, z, y == top ? GRASS : y >= top - 3 ? DIRT : STONE);
        });
    }

    // where the ground stands three or more blocks over a neighbour the soil falls away, leaving bands of bare rock
    private void rockFaces() {
        each((x, z) -> {
            int low = top(x, z);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                int nx = x + side.getStepX(), nz = z + side.getStepZ();
                if (inside(nx, nz)) low = Math.min(low, top(nx, nz));
            }
            for (int y = low + 1; y <= top(x, z) - 2; y++) {
                set(x, y, z, Math.sin(y * 1.3 + x * 0.4 + z * 0.3 + seeds[6]) > 0.3 ? ANDESITE : STONE);
            }
        });
    }

    private void oakAt(double angle, double out) {
        int x = (int) Math.round(Math.cos(angle) * out), z = (int) Math.round(Math.sin(angle) * out);
        if (!inside(x, z) || shot(x, z) || distance(x, z) <= FLOOR_REACH + 3) return;
        int base = top(x, z), height = 5 + random.nextInt(3);
        if (!get(x, base, z).is(Blocks.GRASS_BLOCK)) return;
        for (int y = 1; y <= height; y++) set(x, base + y, z, LOG);
        int crown = base + height;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    double r = dx * dx + dz * dz + dy * dy * 1.6;
                    if (r > 9.5 || r > 7 && random.nextFloat() < 0.4 || dx == 0 && dz == 0 && dy <= 0) continue;
                    if (get(x + dx, crown + dy, z + dz).isAir()) set(x + dx, crown + dy, z + dz, LEAVES);
                }
            }
        }
    }

    private void fallenRocks() {
        for (int i = 0; i < FALLEN_ROCKS; i++) {
            int radius = 1 + random.nextInt(2), out = radius + 3 + random.nextInt(8);
            int x = i % 2 == 0 ? out : -out, z = (int) Math.round(face(x)) - 1 - random.nextInt(3);
            if (!inside(x, z)) continue;
            int base = top(x, z);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -1; dy <= radius; dy++) {
                        double r = (dx * dx + dz * dz) / (double) (radius * radius) + dy * dy / (radius * 1.3 * radius * 1.3);
                        if (r > 1 - random.nextDouble() * 0.12) continue;
                        boolean upper = dy >= radius - 1 && random.nextFloat() < 0.5;
                        set(x + dx, base + dy, z + dz, upper ? MOSS : random.nextBoolean() ? ANDESITE : STONE);
                    }
                }
            }
        }
    }

    // short and tall grass on open ground; the floor stays short so feet can be seen
    private void cover(double grass, double tall) {
        each((x, z) -> {
            int y = top(x, z);
            if (!get(x, y, z).is(Blocks.GRASS_BLOCK) || !get(x, y + 1, z).isAir()) return;
            boolean flat = distance(x, z) < FLOOR_REACH;
            float roll = random.nextFloat();
            if (!flat && roll < tall && get(x, y + 2, z).isAir()) {
                set(x, y + 1, z, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                set(x, y + 2, z, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            } else if (roll < tall + grass * (flat ? 0.6 : 1)) {
                set(x, y + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
            }
        });
    }

    private void flowers() {
        int kind = random.nextInt(FLOWERS.size());
        for (int i = 0; i < FLOWER_PATCHES; i++) {
            double a = angle(), out = FLOOR_REACH - 4 + random.nextDouble() * 16;
            BlockState flower = FLOWERS.get((kind + i) % FLOWERS.size());
            for (int n = 5 + random.nextInt(6); n > 0; n--) {
                int x = (int) Math.round(Math.cos(a) * out + random.nextGaussian() * PATCH_SPREAD);
                int z = (int) Math.round(Math.sin(a) * out + random.nextGaussian() * PATCH_SPREAD);
                if (!inside(x, z)) continue;
                int y = top(x, z);
                BlockState over = get(x, y + 1, z);
                if (get(x, y, z).is(Blocks.GRASS_BLOCK) && (over.isAir() || over.is(Blocks.SHORT_GRASS))) set(x, y + 1, z, flower);
            }
        }
    }

    private void wayHome(Zones.Zone zone) {
        zone.rx = centre.getX() + 0.5;
        zone.ry = floor + 1;
        zone.rz = centre.getZ() + HOME - 1 + 0.5 - (GAP - 0.5);
        zone.nx = 0;
        zone.nz = -1;
        zone.ax = zone.rx;
        zone.ay = zone.ry;
        zone.az = zone.rz - Trips.STEP_IN;
        zone.yaw = 180;
        zone.cy = top(0, 0) + 1;
    }
}
