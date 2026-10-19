package dev.hyperspacewormholes.wormholes.ride;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Looks for flat, dry ground to drop you out of a wormhole onto, checking a few spots a tick while the screen's white.
 * Gives up after 10 seconds and takes the best it found.
 */
final class FlatLandingSearch {
    private static final int SHIFT = 8192;
    private static final int MAX_SHIFTS = 32;
    private static final int GIVE_UP_TICKS = 10 * 20;
    private static final int WATER_TICKS = 100;
    private static final long BUDGET_NANOS = 25_000_000L;

    private static final int GRID_STEP = 32;
    private static final int AXIS_RANGE = 4096;
    private static final int ACROSS_RANGE = 2048;

    private static final int BEHIND = 3;
    private static final int AHEAD = 3;
    private static final int HALF_WIDTH = 3;
    private static final int MAX_STEP = 7;
    private static final int SCREEN_STEP = 2;

    private final ServerLevel level;
    private final WormholeDestinations.Plan plan;
    private final boolean wantWater;
    private final ChunkGenerator generator;
    private final RandomState randomState;
    private final int[][] candidates;
    private int next;
    private int ticks;
    private int shifts;
    private int alongOffset;
    private @Nullable BlockPos found;
    private @Nullable BlockPos bestFlat;
    private @Nullable BlockPos bestRough;
    private double bestRoughScore = Double.MAX_VALUE;
    private boolean done;

    private FlatLandingSearch(ServerLevel level, WormholeDestinations.Plan plan) {
        this.level = level;
        this.plan = plan;
        this.wantWater = plan.type() == WormholeType.BLUE;
        this.generator = level.getChunkSource().getGenerator();
        this.randomState = level.getChunkSource().randomState();
        List<int[]> grid = new ArrayList<>();
        for (int along = -AXIS_RANGE; along <= AXIS_RANGE; along += GRID_STEP) {
            for (int across = -ACROSS_RANGE; across <= ACROSS_RANGE; across += GRID_STEP) grid.add(new int[]{along, across});
        }
        grid.sort(Comparator.comparingInt(c -> c[0] * c[0] + c[1] * c[1]));
        this.candidates = grid.toArray(int[][]::new);
    }

    static @Nullable FlatLandingSearch start(MinecraftServer server, WormholeDestinations.Plan plan) {
        ServerLevel level = server.getLevel(plan.dimension());
        if (level == null || level.dimensionType().hasCeiling()) return null;
        return new FlatLandingSearch(level, plan);
    }

    @Nullable BlockPos result() {
        if (found != null) return found;
        return bestFlat != null ? bestFlat : shifts >= MAX_SHIFTS ? bestRough : null;
    }

    boolean step() {
        if (done) return true;
        ticks++;
        long until = System.nanoTime() + BUDGET_NANOS;
        while (next < candidates.length && System.nanoTime() < until) {
            check(next++);
            if (found != null) break;
            if (bestFlat != null && (!wantWater || ticks >= WATER_TICKS)) {
                found = bestFlat;
                break;
            }
        }
        if (found == null && ticks >= GIVE_UP_TICKS) {
            found = bestFlat != null ? bestFlat : bestRough;
            done = true;
            return true;
        }
        if (found == null && bestFlat != null && next >= candidates.length) found = bestFlat;
        if (found == null && next >= candidates.length) {
            if (++shifts >= MAX_SHIFTS) {
                done = true;
                return true;
            }
            int step = (shifts + 1) / 2 * SHIFT;
            alongOffset = shifts % 2 == 1 ? step : -step;
            next = 0;
        }
        if (found != null) done = true;
        return done;
    }

    private void check(int index) {
        int offset = candidates[index][0] + alongOffset, across = candidates[index][1];
        int sideX = -plan.dirZ(), sideZ = plan.dirX();
        int x = plan.x() + plan.dirX() * offset + sideX * across;
        int z = plan.z() + plan.dirZ() * offset + sideZ * across;
        if (!level.getWorldBorder().isWithinBounds(x, z)) return;
        if (!looksFlat(x, z)) return;

        int y0 = groundY(x, z);
        if (y0 == Integer.MIN_VALUE) return;
        int maxStep = 0;
        double score = 0;
        for (int a = -BEHIND; a <= AHEAD; a++) {
            for (int b = -HALF_WIDTH; b <= HALF_WIDTH; b++) {
                int y = groundY(x + plan.dirX() * a + sideX * b, z + plan.dirZ() * a + sideZ * b);
                if (y == Integer.MIN_VALUE) return;
                int d = Math.abs(y - y0);
                maxStep = Math.max(maxStep, d);
                score += d * d;
            }
        }
        BlockPos feet = new BlockPos(x, y0, z);
        if (maxStep <= MAX_STEP) {
            if (wantWater && WormholeDestinations.nearWater(level, feet)) found = feet;
            else if (bestFlat == null) bestFlat = feet;
        } else if (score < bestRoughScore) {
            bestRoughScore = score;
            bestRough = feet;
        }
    }

    private boolean looksFlat(int x, int z) {
        int y0 = generatedHeight(x, z);
        if (y0 == Integer.MIN_VALUE) return false;
        int sideX = -plan.dirZ(), sideZ = plan.dirX();
        for (int a = -BEHIND; a <= AHEAD; a += SCREEN_STEP) {
            for (int b = -HALF_WIDTH; b <= HALF_WIDTH; b += SCREEN_STEP) {
                int y = generatedHeight(x + plan.dirX() * a + sideX * b, z + plan.dirZ() * a + sideZ * b);
                if (y == Integer.MIN_VALUE || Math.abs(y - y0) > MAX_STEP) return false;
            }
        }
        return true;
    }

    private int generatedHeight(int x, int z) {
        int ground = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
        if (ground <= level.getMinBuildHeight() + 1) return Integer.MIN_VALUE;
        int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
        return UltraWarpRules.isUnderwater(level) || surface <= ground ? ground : Integer.MIN_VALUE;
    }

    private int groundY(int x, int z) {
        boolean underwater = UltraWarpRules.isUnderwater(level);
        int y = level.getHeight(underwater ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int bottom = level.getMinBuildHeight() + 1;
        int terrainTop = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (y > bottom) {
            BlockState below = level.getBlockState(pos.set(x, y - 1, z));
            boolean grown = y - 1 >= terrainTop && !below.is(Blocks.WATER) && !below.is(Blocks.LAVA)
                    && !LandingClearing.isBuilt(level, pos, below);
            if (!below.getFluidState().isEmpty() && !underwater && !grown) return Integer.MIN_VALUE;
            if (below.isAir() || grown || LandingClearing.isVegetation(below)
                    || below.getCollisionShape(level, pos).isEmpty()) {
                y--;
                continue;
            }
            return WormholeDestinations.isHazard(below) ? Integer.MIN_VALUE : y;
        }
        return Integer.MIN_VALUE;
    }
}
