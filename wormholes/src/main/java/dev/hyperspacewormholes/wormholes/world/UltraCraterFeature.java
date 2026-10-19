package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Ultra Valley's craters: raised, grooved cones of earth with grassy rims and floors, some sunk into pits. One per
 * cell.
 */
public class UltraCraterFeature extends Feature<NoneFeatureConfiguration> {
    private static final int CELL = 40;
    private static final int MARGIN = 6;
    private static final int CHANCE = 80;
    private static final int BIG_CHANCE = 15;
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final Map<Long, Crater> CRATERS = CellFeature.cache(64);
    private static BlockState[] patches;

    private static final BlockState GRASS = Blocks.MOSS_BLOCK.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState SIDE = Blocks.YELLOW_TERRACOTTA.defaultBlockState();
    private static final BlockState GROOVE = Blocks.TERRACOTTA.defaultBlockState();
    private static final BlockState PIT = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState GLOW = Blocks.SHROOMLIGHT.defaultBlockState();

    record Crater(int x, int z, int base, double rim, double slope, int height, int depth, boolean pit, double wobble, double phase) {}

    enum Part { FLOOR, PIT, RIM, SIDE }

    record Top(Crater crater, Part part, double y, double angle) {}

    public UltraCraterFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        CellFeature.Ground ground = CellFeature.Ground.of(context);
        ChunkPos chunk = new ChunkPos(context.origin());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                Top top = top(ground, x, z);
                int current = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (top == null) {
                    if (level.getBlockState(p.set(x, current, z)).is(Blocks.PODZOL)) {
                        level.setBlock(p, patch(CellFeature.hash(x, z)), FLAGS);
                    }
                    continue;
                }
                int target = Mth.floor(top.y());
                for (int y = current + 1; y <= target; y++) level.setBlock(p.set(x, y, z), fill(top, target - y), FLAGS);
                for (int y = target + 1; y <= current; y++) level.setBlock(p.set(x, y, z), Blocks.AIR.defaultBlockState(), FLAGS);
                if (target <= current) {
                    for (int d = 0; d <= Math.min(3, target - level.getMinBuildHeight() - 1); d++) {
                        level.setBlock(p.set(x, target - d, z), fill(top, d), FLAGS);
                    }
                }
            }
        }
        return true;
    }

    private static BlockState fill(Top top, int depth) {
        return switch (top.part()) {
            case FLOOR, RIM -> depth == 0 ? GRASS : DIRT;
            case PIT -> depth > 0 ? DIRT : (mix(top) % 9 == 0 ? GLOW : mix(top) % 3 == 0 ? patch(mix(top)) : PIT);
            case SIDE -> depth > 3 ? DIRT
                    : Math.floorMod((int) Math.floor(top.angle() * top.crater().rim() / 1.6), 4) == 0 ? GROOVE : SIDE;
        };
    }

    private static synchronized BlockState patch(long hash) {
        if (patches == null) {
            List<BlockState> found = new ArrayList<>(List.of(Blocks.PODZOL.defaultBlockState(), DIRT, PIT));
            ResourceLocation gravel = ResourceLocation.fromNamespaceAndPath("chipped", "dirty_gravel");
            if (BuiltInRegistries.BLOCK.containsKey(gravel)) found.add(BuiltInRegistries.BLOCK.get(gravel).defaultBlockState());
            patches = found.toArray(BlockState[]::new);
        }
        return patches[(int) Math.floorMod(hash, (long) patches.length)];
    }

    private static long mix(Top top) {
        long h = Double.doubleToLongBits(top.y() * 31 + top.angle() * 997);
        h ^= h >>> 29;
        return Math.abs(h * 0x9E3779B97F4A7C15L >>> 40);
    }

    static @Nullable Top top(CellFeature.Ground ground, int x, int z) {
        Top best = null;
        for (int cx = Math.floorDiv(x, CELL) - 1; cx <= Math.floorDiv(x, CELL) + 1; cx++) {
            for (int cz = Math.floorDiv(z, CELL) - 1; cz <= Math.floorDiv(z, CELL) + 1; cz++) {
                Crater crater = crater(ground, cx, cz);
                if (crater == null) continue;
                Top top = top(crater, x, z);
                if (top != null && (best == null || top.y() > best.y())) best = top;
            }
        }
        return best;
    }

    static int surface(CellFeature.Ground ground, int x, int z) {
        Top top = top(ground, x, z);
        return top == null ? ground.at(x, z) : Mth.floor(top.y()) + 1;
    }

    private static @Nullable Top top(Crater c, int x, int z) {
        double dx = x + 0.5 - c.x(), dz = z + 0.5 - c.z();
        double d = Math.sqrt(dx * dx + dz * dz);
        double angle = Math.atan2(dz, dx);
        double rim = c.rim() * (1 + c.wobble() * Math.sin(3 * angle + c.phase()));
        if (d > rim + c.slope()) return null;
        if (c.pit() && d <= rim * 0.7) return new Top(c, Part.PIT, c.base() + c.height() - c.depth() - 4 + 2 * d / rim, angle);
        if (d <= rim - 2) return new Top(c, Part.FLOOR, c.base() + c.height() - c.depth(), angle);
        if (d <= rim) return new Top(c, Part.RIM, c.base() + c.height(), angle);
        double t = (d - rim) / c.slope();
        return new Top(c, Part.SIDE, c.base() + c.height() * Math.pow(1 - t, 1.5), angle);
    }

    private static @Nullable Crater crater(CellFeature.Ground ground, int cx, int cz) {
        long key = ground.seed() ^ ChunkPos.asLong(cx, cz) ^ 0xC4A7E4L;
        synchronized (CRATERS) {
            if (CRATERS.containsKey(key)) return CRATERS.get(key);
        }
        RandomSource random = CellFeature.random(ground.seed(), cx, cz, 0xC4A7E4L);
        int x = cx * CELL + MARGIN + random.nextInt(CELL - 2 * MARGIN);
        int z = cz * CELL + MARGIN + random.nextInt(CELL - 2 * MARGIN);
        Crater crater = null;
        if (random.nextInt(100) < CHANCE && ground.dry(x, z)) {
            boolean big = random.nextInt(100) < BIG_CHANCE;
            double rim = big ? 15 + random.nextDouble() * 6 : 5 + random.nextDouble() * 7;
            int height = big ? 12 + random.nextInt(6) : 5 + random.nextInt(7);
            crater = new Crater(x, z, ground.at(x, z) - 1, rim, rim * 0.7 + 5, height, 2 + random.nextInt(2), big,
                    0.04 + random.nextDouble() * 0.06, random.nextDouble() * Math.PI * 2);
        }
        synchronized (CRATERS) {
            CRATERS.put(key, crater);
        }
        return crater;
    }
}
