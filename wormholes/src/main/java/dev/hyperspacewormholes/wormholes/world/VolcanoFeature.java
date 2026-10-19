package dev.hyperspacewormholes.wormholes.world;

import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static dev.hyperspacewormholes.wormholes.world.CellFeature.mix;

/**
 * The Ultra Jungle's volcanoes in Biomes O' Plenty's volcano biome: basalt cones with lava craters and magma streaks.
 */
public class VolcanoFeature extends Feature<NoneFeatureConfiguration> {
    private static final int CELL = 320;
    private static final int SAMPLE_STEP = 16;
    private static final long VOLCANO_TEMPERATURE = 7000;
    private static final int MIN_HEIGHT = 55;
    private static final int MAX_HEIGHT = 85;
    private static final double SPREAD = 1.4;
    private static final int MAX_REACH = (int) Math.ceil(MAX_HEIGHT * SPREAD) + 4;
    private static final Map<Long, Volcano> VOLCANOES = CellFeature.cache(16);

    private static final BlockState BASALT = Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState SMOOTH_BASALT = Blocks.SMOOTH_BASALT.defaultBlockState();
    private static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private record Volcano(int x, int z, int ground, int height, double crater, double depth, long shape) {
        double radius() {
            return height * SPREAD;
        }

        double cone(double d) {
            return ground + height * Math.pow(Math.max(0, 1 - d / radius()), 1.25);
        }

        double rim() {
            return cone(crater);
        }

        double floor(double d) {
            double f = d / crater;
            return rim() - depth * (1 - f * f);
        }

        int lava() {
            return (int) Math.floor(rim() - depth * 0.5);
        }
    }

    public VolcanoFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (level.getLevel().dimension() != Wormholes.ULTRA_JUNGLE) return false;
        CellFeature.Ground ground = CellFeature.Ground.of(context);
        int x0 = (context.origin().getX() >> 4) << 4, z0 = (context.origin().getZ() >> 4) << 4;
        for (int cx = Math.floorDiv(x0 - MAX_REACH, CELL); cx <= Math.floorDiv(x0 + 15 + MAX_REACH, CELL); cx++) {
            for (int cz = Math.floorDiv(z0 - MAX_REACH, CELL); cz <= Math.floorDiv(z0 + 15 + MAX_REACH, CELL); cz++) {
                Volcano volcano = volcano(ground, cx, cz);
                if (volcano != null) build(level, volcano, x0, z0);
            }
        }
        return true;
    }

    static boolean onVolcano(CellFeature.Ground ground, int x, int z) {
        for (int cx = Math.floorDiv(x - MAX_REACH, CELL); cx <= Math.floorDiv(x + MAX_REACH, CELL); cx++) {
            for (int cz = Math.floorDiv(z - MAX_REACH, CELL); cz <= Math.floorDiv(z + MAX_REACH, CELL); cz++) {
                Volcano v = volcano(ground, cx, cz);
                if (v == null) continue;
                double dx = x - v.x(), dz = z - v.z();
                if (dx * dx + dz * dz < v.radius() * v.radius()) return true;
            }
        }
        return false;
    }

    private static @Nullable Volcano volcano(CellFeature.Ground ground, int cx, int cz) {
        long key = ground.seed() ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        synchronized (VOLCANOES) {
            if (VOLCANOES.containsKey(key)) return VOLCANOES.get(key);
        }
        Climate.Sampler sampler = ground.randomState().sampler();
        long hottest = Long.MIN_VALUE;
        int bestX = 0, bestZ = 0;
        for (int x = cx * CELL + SAMPLE_STEP / 2; x < (cx + 1) * CELL; x += SAMPLE_STEP) {
            for (int z = cz * CELL + SAMPLE_STEP / 2; z < (cz + 1) * CELL; z += SAMPLE_STEP) {
                long t = sampler.sample(x >> 2, 0, z >> 2).temperature();
                if (t > hottest) {
                    hottest = t;
                    bestX = x;
                    bestZ = z;
                }
            }
        }
        Volcano volcano = null;
        if (hottest >= VOLCANO_TEMPERATURE) {
            RandomSource random = RandomSource.create(key ^ 0x7015CA40L);
            int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
            volcano = new Volcano(bestX, bestZ, ground.at(bestX, bestZ), height, 9 + random.nextInt(6),
                    12 + random.nextInt(7), random.nextLong());
        }
        synchronized (VOLCANOES) {
            VOLCANOES.put(key, volcano);
        }
        return volcano;
    }

    private static void build(WorldGenLevel level, Volcano v, int x0, int z0) {
        double radius = v.radius();
        if (x0 + 15 < v.x() - radius || x0 > v.x() + radius || z0 + 15 < v.z() - radius || z0 > v.z() + radius) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int lava = v.lava();
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                double dx = x + 0.5 - v.x(), dz = z + 0.5 - v.z();
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d >= radius) continue;
                double rough = ((mix(v.shape() ^ BlockPos.asLong(x, 0, z)) & 3) - 1.5) * 0.5 * Math.min(1, d / v.crater());
                boolean inCrater = d < v.crater();
                int top = (int) Math.floor(inCrater ? v.floor(d) : v.cone(d) + rough);
                int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                boolean streak = !inCrater && onStreak(v, dx, dz, d);
                for (int y = Math.min(ground, top) - 3; y <= top; y++) {
                    BlockState state = y >= top - 1 && (streak || inCrater || d < v.crater() + 3) ? MAGMA : rock(v.shape(), x, y, z);
                    level.setBlock(pos.set(x, y, z), state, Block.UPDATE_CLIENTS);
                }
                if (inCrater) {
                    for (int y = top + 1; y <= Math.max(ground, lava); y++) {
                        level.setBlock(pos.set(x, y, z), y <= lava ? LAVA : AIR, Block.UPDATE_CLIENTS);
                    }
                } else if (top > ground) {
                    for (int y = top + 1; y <= top + 3; y++) {
                        if (!level.getBlockState(pos.set(x, y, z)).isAir()) level.setBlock(pos, AIR, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static boolean onStreak(Volcano v, double dx, double dz, double d) {
        double angle = Math.atan2(dz, dx);
        for (int i = 0; i < 5; i++) {
            double a = (mix(v.shape() + i) >>> 11) * 0x1.0p-53 * Math.PI * 2;
            double wiggle = Math.sin(d * 0.15 + i) * 0.08;
            double width = 2.2 * (1 - d / v.radius()) + 0.6;
            double diff = Math.abs(Mth.wrapDegrees(Math.toDegrees(angle - a - wiggle))) * Math.PI / 180 * d;
            if (diff < width && d < v.radius() * (0.55 + 0.1 * i % 3)) return true;
        }
        return false;
    }

    private static BlockState rock(long shape, int x, int y, int z) {
        long h = mix(shape ^ BlockPos.asLong(x, y, z)) & 15;
        return h < 9 ? BASALT : h < 13 ? BLACKSTONE : SMOOTH_BASALT;
    }
}
