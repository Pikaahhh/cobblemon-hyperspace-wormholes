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
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixes patches of different sands from Chipped or Biomes O' Plenty (whichever's installed) into the Ultra Desert's
 * sand.
 */
public class UltraDesertSandFeature extends Feature<NoneFeatureConfiguration> {
    private static final List<String> SANDS = List.of("minecraft:sand", "chipped:white_sand", "chipped:desert_sand",
            "chipped:coarse_sand", "chipped:soft_sand");
    private static final double SCALE = 1 / 36.0;

    private static long noiseSeed;
    private static SimplexNoise noise;
    private static List<BlockState> sands;

    public UltraDesertSandFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        SimplexNoise patches = noise(level.getSeed());
        List<BlockState> choices = sands();
        if (choices.size() < 2) return false;
        ChunkPos chunk = new ChunkPos(context.origin());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                double v = patches.getValue(x * SCALE, z * SCALE) * 0.85 + patches.getValue(x * SCALE * 4, z * SCALE * 4) * 0.15;
                BlockState sand = choices.get(Mth.clamp((int) ((v + 1) / 2 * choices.size()), 0, choices.size() - 1));
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                for (int y = top; y > top - 4; y--) {
                    if (level.getBlockState(p.set(x, y, z)).is(Blocks.SAND)) level.setBlock(p, sand, Block.UPDATE_CLIENTS);
                }
            }
        }
        return true;
    }

    private static synchronized SimplexNoise noise(long seed) {
        if (noise == null || noiseSeed != seed) {
            noise = new SimplexNoise(RandomSource.create(seed ^ 0x5A4D5L));
            noiseSeed = seed;
        }
        return noise;
    }

    private static synchronized List<BlockState> sands() {
        if (sands == null) {
            sands = new ArrayList<>();
            for (String id : SANDS) add(id);
            if (sands.size() == 1) add("biomesoplenty:white_sand");
        }
        return sands;
    }

    private static void add(String id) {
        ResourceLocation location = ResourceLocation.parse(id);
        if (BuiltInRegistries.BLOCK.containsKey(location)) sands.add(BuiltInRegistries.BLOCK.get(location).defaultBlockState());
    }
}
