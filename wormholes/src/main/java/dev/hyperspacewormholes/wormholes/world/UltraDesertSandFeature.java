package dev.hyperspacewormholes.wormholes.world;

import dev.hyperspacewormholes.wormholes.DesertBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Turns the Ultra Desert's sand white: the top DEPTH sand blocks of every column, the world's and the zone's alike (it
 * runs after the zone and the formations), become the mod's white sand (DesertBlocks), with soft drifts of Chipped's
 * white sand (a touch warmer) and pearl sand (a touch pinker) laid over it by two octaves of noise, WARM and PEARL of
 * the ground each, at SCALE. Without Chipped it stays the one white sand.
 */
public class UltraDesertSandFeature extends CellFeature {
    private static final String WARM_SAND = "chipped:white_sand", PEARL_SAND = "chipped:pearl_sand";
    private static final int DEPTH = 4;
    private static final double SCALE = 1 / 28.0, WARM = 0.42, PEARL = -0.42;

    private static long noiseSeed;
    private static SimplexNoise noise;

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        BlockState sand = DesertBlocks.WHITE_SAND.defaultBlockState();
        BlockState warm = mixIn(WARM_SAND, sand), pearl = mixIn(PEARL_SAND, sand);
        SimplexNoise drifts = noise(level.getSeed());
        ChunkPos chunk = new ChunkPos(origin);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                double v = drifts.getValue(x * SCALE, z * SCALE) * 0.8 + drifts.getValue(x * SCALE * 4, z * SCALE * 4) * 0.2;
                BlockState pick = v > WARM ? warm : v < PEARL ? pearl : sand;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                for (int y = top; y > top - DEPTH; y--) {
                    BlockState here = level.getBlockState(p.set(x, y, z));
                    if ((here.is(Blocks.SAND) || here == sand) && here != pick) level.setBlock(p, pick, Block.UPDATE_CLIENTS);
                }
            }
        }
        return true;
    }

    private static BlockState mixIn(String id, BlockState sand) {
        BlockState state = Zone.block(id);
        return state.isAir() || state.is(Blocks.SAND) ? sand : state;
    }

    private static synchronized SimplexNoise noise(long seed) {
        if (noise == null || noiseSeed != seed) {
            noise = new SimplexNoise(RandomSource.create(seed ^ 0x5A4D5L));
            noiseSeed = seed;
        }
        return noise;
    }
}
