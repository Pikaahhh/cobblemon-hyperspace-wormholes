package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base for big world features laid out one per cell from the world seed. Each chunk only builds its own slice of every
 * nearby shape.
 */
public abstract class CellFeature extends Feature<NoneFeatureConfiguration> {
    private final int cell;
    private final int margin;
    private final int reach;
    private final long salt;
    private final Map<Long, Map<Long, List<Map.Entry<BlockPos, BlockState>>>> shapes = cache(32);

    public record Ground(ChunkGenerator generator, LevelHeightAccessor level, RandomState randomState, long seed) {
        static Ground of(FeaturePlaceContext<?> context) {
            WorldGenLevel level = context.level();
            return new Ground(context.chunkGenerator(), level, level.getLevel().getChunkSource().randomState(), level.getSeed());
        }

        public int at(int x, int z) {
            return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
        }

        public boolean dry(int x, int z) {
            return at(x, z) <= generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
        }
    }

    protected CellFeature(int cell, int margin, int reach, long salt) {
        super(NoneFeatureConfiguration.CODEC);
        this.cell = cell;
        this.margin = margin;
        this.reach = reach;
        this.salt = salt;
    }

    protected abstract @Nullable Map<BlockPos, BlockState> shape(RandomSource random, int x, int z, Ground ground);

    protected boolean replaces(BlockState existing, BlockState placed) {
        return !existing.is(Blocks.BEDROCK);
    }

    protected void placed(WorldGenLevel level, BlockPos pos, BlockState state) {}

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        Ground ground = Ground.of(context);
        int x0 = (context.origin().getX() >> 4) << 4, z0 = (context.origin().getZ() >> 4) << 4;
        long chunk = ChunkPos.asLong(context.origin());
        for (int cx = Math.floorDiv(x0 - reach, cell); cx <= Math.floorDiv(x0 + 15 + reach, cell); cx++) {
            for (int cz = Math.floorDiv(z0 - reach, cell); cz <= Math.floorDiv(z0 + 15 + reach, cell); cz++) {
                List<Map.Entry<BlockPos, BlockState>> blocks = byChunk(cx, cz, ground).get(chunk);
                if (blocks == null) continue;
                for (Map.Entry<BlockPos, BlockState> b : blocks) {
                    BlockPos p = b.getKey();
                    if (p.getY() <= level.getMinBuildHeight() || p.getY() >= level.getMaxBuildHeight()) continue;
                    if (!replaces(level.getBlockState(p), b.getValue())) continue;
                    level.setBlock(p, b.getValue(), Block.UPDATE_CLIENTS);
                    placed(level, p, b.getValue());
                }
            }
        }
        return true;
    }

    private Map<Long, List<Map.Entry<BlockPos, BlockState>>> byChunk(int cx, int cz, Ground ground) {
        long key = ground.seed() ^ salt ^ ChunkPos.asLong(cx, cz);
        synchronized (shapes) {
            Map<Long, List<Map.Entry<BlockPos, BlockState>>> known = shapes.get(key);
            if (known != null) return known;
        }
        RandomSource random = random(ground.seed(), cx, cz, salt);
        int x = cx * cell + margin + random.nextInt(cell - 2 * margin);
        int z = cz * cell + margin + random.nextInt(cell - 2 * margin);
        Map<BlockPos, BlockState> blocks = shape(random, x, z, ground);
        Map<Long, List<Map.Entry<BlockPos, BlockState>>> split = new HashMap<>();
        if (blocks != null) {
            for (Map.Entry<BlockPos, BlockState> b : blocks.entrySet()) {
                split.computeIfAbsent(ChunkPos.asLong(b.getKey()), c -> new ArrayList<>()).add(b);
            }
        }
        synchronized (shapes) {
            shapes.put(key, split);
        }
        return split;
    }

    static RandomSource random(long seed, int cx, int cz, long salt) {
        return RandomSource.create(seed ^ salt ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL));
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
}
