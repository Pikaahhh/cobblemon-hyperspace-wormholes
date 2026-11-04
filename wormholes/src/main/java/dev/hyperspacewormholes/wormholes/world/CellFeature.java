package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base for the mod's world features (no configuration), with the helpers they share to lay big shapes out one per cell
 * from the world seed, each chunk building only its own slice of every nearby shape.
 */
public abstract class CellFeature extends Feature<NoneFeatureConfiguration> {

    public record Ground(ChunkGenerator generator, LevelHeightAccessor level, RandomState randomState, long seed) {
        static Ground of(WorldGenLevel level, ChunkGenerator chunkGenerator) {
            return new Ground(chunkGenerator, level, level.getLevel().getChunkSource().randomState(), level.getSeed());
        }

        public int at(int x, int z) {
            return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
        }

        public int lowest(int x, int z, int reach) {
            int h = reach / 2, low = at(x, z);
            for (int[] d : new int[][]{{-h, -h}, {h, -h}, {-h, h}, {h, h}, {0, -h}, {0, h}, {-h, 0}, {h, 0}}) {
                low = Math.min(low, at(x + d[0], z + d[1]));
            }
            return low;
        }
    }

    protected CellFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    public abstract boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin);

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return place(context.level(), context.chunkGenerator(), context.random(), context.origin());
    }

    static RandomSource random(long seed, int cx, int cz, long salt) {
        return RandomSource.create(seed ^ salt ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL));
    }

    static long cellHash(long seed, int cx, int cz, long salt) {
        return mix(seed ^ cx * 0x9E3779B97F4A7C15L ^ cz * 0xC2B2AE3D27D4EB4FL ^ salt);
    }

    static <K, V> Map<K, V> cache(int limit) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > limit;
            }
        };
    }

    static long hash(int x, int z) {
        long h = (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 31;
        return Math.abs(h * 0xBF58476D1CE4E5B9L >>> 7);
    }

    static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        return h ^ (h >>> 33);
    }

    /** A hash's top 24 bits as a number in [0, 1). */
    static double unit(long h) {
        return (h >>> 40) / (double) (1L << 24);
    }
}
