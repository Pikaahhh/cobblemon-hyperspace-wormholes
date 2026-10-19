package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Grows swole trees around the Ultra Jungle, one per cell from the world seed, each chunk building its own part.
 */
public class SwoleTreeFeature extends Feature<NoneFeatureConfiguration> {
    private static final int CELL = 272;
    private static final int MARGIN = 48;
    private static final int REACH = 80;
    private static final Map<Long, Tree> TREES = CellFeature.cache(12);

    private record Tree(BlockPos base, SwoleTree.Shape shape, Map<Long, List<Map.Entry<BlockPos, BlockState>>> chunks) {}

    public SwoleTreeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        CellFeature.Ground ground = CellFeature.Ground.of(context);
        int x0 = (context.origin().getX() >> 4) << 4, z0 = (context.origin().getZ() >> 4) << 4;
        long chunk = ChunkPos.asLong(context.origin());
        for (int cx = Math.floorDiv(x0 - REACH, CELL); cx <= Math.floorDiv(x0 + 15 + REACH, CELL); cx++) {
            for (int cz = Math.floorDiv(z0 - REACH, CELL); cz <= Math.floorDiv(z0 + 15 + REACH, CELL); cz++) {
                Tree tree = tree(ground, cx, cz);
                if (tree == null) continue;
                List<Map.Entry<BlockPos, BlockState>> blocks = tree.chunks().get(chunk);
                if (blocks != null) SwoleTree.buildSlice(context.level(), tree.shape(), blocks, chunk);
            }
        }
        return true;
    }

    public static @Nullable BlockPos platform(ServerLevel level, int x, int z) {
        var ground = new CellFeature.Ground(level.getChunkSource().getGenerator(), level,
                level.getChunkSource().randomState(), level.getSeed());
        Tree nearest = null;
        long best = Long.MAX_VALUE;
        for (int cx = Math.floorDiv(x, CELL) - 1; cx <= Math.floorDiv(x, CELL) + 1; cx++) {
            for (int cz = Math.floorDiv(z, CELL) - 1; cz <= Math.floorDiv(z, CELL) + 1; cz++) {
                Tree tree = tree(ground, cx, cz);
                if (tree == null) continue;
                long dx = tree.base().getX() - x, dz = tree.base().getZ() - z;
                if (dx * dx + dz * dz < best) {
                    best = dx * dx + dz * dz;
                    nearest = tree;
                }
            }
        }
        if (nearest == null) return null;
        BlockPos stand = nearest.base().above(nearest.shape().height());
        level.getChunk(stand.getX() >> 4, stand.getZ() >> 4);
        return SwoleTree.isWood(level.getBlockState(stand.below())) && level.getBlockState(stand).isAir()
                && level.getBlockState(stand.above()).isAir() ? stand : null;
    }

    private static @Nullable Tree tree(CellFeature.Ground ground, int cx, int cz) {
        long key = ground.seed() ^ ChunkPos.asLong(cx, cz);
        synchronized (TREES) {
            if (TREES.containsKey(key)) return TREES.get(key);
        }
        RandomSource random = CellFeature.random(ground.seed(), cx, cz, 0x5701E7AEEL);
        Tree tree = null;
        for (int attempt = 0; attempt < 8 && tree == null; attempt++) {
            int x = cx * CELL + MARGIN + random.nextInt(CELL - 2 * MARGIN);
            int z = cz * CELL + MARGIN + random.nextInt(CELL - 2 * MARGIN);
            if (!ground.dry(x, z) || VolcanoFeature.onVolcano(ground, x, z)) continue;
            BlockPos base = new BlockPos(x, ground.at(x, z), z);
            SwoleTree.Shape shape = SwoleTree.shape(base, random);
            tree = new Tree(base, shape, shape.byChunk());
        }
        synchronized (TREES) {
            TREES.put(key, tree);
        }
        return tree;
    }
}
