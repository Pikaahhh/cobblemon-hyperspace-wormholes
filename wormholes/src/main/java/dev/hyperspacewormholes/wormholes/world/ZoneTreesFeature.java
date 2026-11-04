package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A zone's own swole trees (Zone files NAME_treeN, cut from the zone by tools/zones/ub/build.py) standing on their own across its world: maybe one in each CELL-block square (CHANCE), at a hashed spot, tree and quarter turn, none within CLEAR of a copy of the zone and none within CELL of another of the same tree (of the two, only the one with the lower rank grows, so every chunk agrees).
 * Each stands with its anchor (the game's canopy height at its trunk) CANOPY above the ground there as the noise lays it (the generator's base height, the same from every chunk, never below the sea), so it towers over the jungle's own canopy.
 * Every chunk it reaches builds its own part; the trunks run on down past the ground and the jungle trees grow up around them afterwards.
 */
public class ZoneTreesFeature extends CellFeature {
    private static final int CELL = 256, EDGE = 32, CLEAR = 400, CANOPY = 30, REACH = 110;
    private static final double CHANCE = 0.5;
    private final String zone;
    private final List<String> trees;

    public ZoneTreesFeature(String zone, List<String> trees) {
        this.zone = zone;
        this.trees = trees;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        ChunkPos chunk = new ChunkPos(origin);
        RandomState noise = level.getLevel().getChunkSource().randomState();
        Zone home = Zone.get(zone);
        int x0 = Math.floorDiv(chunk.getMinBlockX() - REACH, CELL), x1 = Math.floorDiv(chunk.getMaxBlockX() + REACH, CELL);
        int z0 = Math.floorDiv(chunk.getMinBlockZ() - REACH, CELL), z1 = Math.floorDiv(chunk.getMaxBlockZ() + REACH, CELL);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                Spot spot = spot(level.getSeed(), home, cx, cz);
                if (spot == null || crowded(level.getSeed(), home, cx, cz, spot)) continue;
                Zone tree = Zone.get(trees.get(spot.tree()));
                int ground = Math.max(chunkGenerator.getSeaLevel(),
                        chunkGenerator.getBaseHeight(spot.x(), spot.z(), Heightmap.Types.WORLD_SURFACE_WG, level, noise));
                tree.placeTurned(level, chunk, spot.x(), ground + CANOPY, spot.z(), spot.turn());
            }
        }
        return true;
    }

    private record Spot(int x, int z, int tree, Rotation turn, long rank) {}

    private @Nullable Spot spot(long seed, Zone home, int cx, int cz) {
        long h = cellHash(seed, cx, cz, 0x5A1E);
        if (unit(h) >= CHANCE) return null;
        int x = cx * CELL + EDGE + (int) (unit(mix(h + 1)) * (CELL - 2 * EDGE));
        int z = cz * CELL + EDGE + (int) (unit(mix(h + 2)) * (CELL - 2 * EDGE));
        BlockPos centre = home.near(x, z).centre();
        if (Math.hypot(x - centre.getX(), z - centre.getZ()) < CLEAR) return null;
        return new Spot(x, z, (int) (unit(mix(h + 3)) * trees.size()),
                Rotation.values()[(int) (unit(mix(h + 4)) * 4)], mix(h + 5));
    }

    private boolean crowded(long seed, Zone home, int cx, int cz, Spot spot) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                Spot other = spot(seed, home, cx + dx, cz + dz);
                if (other != null && other.tree() == spot.tree() && other.rank() < spot.rank()
                        && Math.hypot(other.x() - spot.x(), other.z() - spot.z()) < CELL) return true;
            }
        }
        return false;
    }
}
