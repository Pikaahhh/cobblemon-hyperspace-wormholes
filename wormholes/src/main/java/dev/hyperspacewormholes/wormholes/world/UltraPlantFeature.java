package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.util.stream.IntStream;

/**
 * The Ultra Plant's towers round its zone, cut from the game's area 282 by the same builder as the zone: plant_282_towerN
 * are the game's three core towers alone and plant_282_outerN the plainer towers in a ring round the core, all in the zone's
 * deepslate. Two PieceLayers (core towers on a wide grid, outer ones on a finer one) keep clear of each other, and nothing
 * stands within ZONE_CLEAR of the Plant's zone; occupied counts that ground as taken so the Xurkitrees keep off it too. The
 * pieces are read on first use, after the mod's own slab and stair blocks exist.
 */
public class UltraPlantFeature extends CellFeature {
    private static final String ZONE = "plant_282";
    private static final int TOWERS = 3, OUTERS = 14;
    private static final int FOOT_SPREAD = 3;
    private static final int CLEARANCE = 4;
    private static final int ZONE_CLEAR = 16;

    private static final class Layers {
        static final PieceLayer CORE = layer("tower", TOWERS, 80, 160, 0x7011E25L,
                (ground, x, z, reach) -> !nearZone(x, z, reach));
        static final PieceLayer OUTER = layer("outer", OUTERS, 30, 96, 0x0B7E25L,
                (ground, x, z, reach) -> !nearZone(x, z, reach) && !nearCore(ground, x, z, reach + CLEARANCE));

        static PieceLayer layer(String piece, int count, int chance, int minCell, long salt, PieceLayer.Fits fits) {
            return new PieceLayer(IntStream.rangeClosed(1, count)
                    .mapToObj(i -> new PieceLayer.Kind(Zone.get(ZONE + "_" + piece + i), 1)).toList(), chance, minCell, salt, fits);
        }
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        Ground ground = Ground.of(level, chunkGenerator);
        ChunkPos chunk = new ChunkPos(origin);
        Layers.CORE.place(level, chunk, ground);
        Layers.OUTER.place(level, chunk, ground);
        return true;
    }

    public static boolean occupied(Ground ground, int x, int z) {
        return nearZone(x, z, 0) || Layers.CORE.covered(ground, x, z, FOOT_SPREAD) || Layers.OUTER.covered(ground, x, z, FOOT_SPREAD);
    }

    private static boolean nearZone(int x, int z, int reach) {
        return Zone.get(ZONE).near(x, z).overlaps(x, z, reach + ZONE_CLEAR);
    }

    private static boolean nearCore(Ground ground, int x, int z, int r) {
        for (int[] d : PieceLayer.spread(r)) {
            if (Layers.CORE.covered(ground, x + d[0], z + d[1], FOOT_SPREAD)) return true;
        }
        return false;
    }
}
