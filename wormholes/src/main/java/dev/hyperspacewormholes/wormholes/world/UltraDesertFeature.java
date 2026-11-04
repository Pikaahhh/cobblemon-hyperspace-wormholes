package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.util.stream.IntStream;

/**
 * The Ultra Desert's rock formations out in the dunes, cut from the game's own area 284 by the zone's builder (Zone files desert_284_rockN, each formation anchored at its own foot with two blocks of root, solid through).
 * Piece 1, the gem-domed plaza behind Pheromosa, stands alone on a wide grid (PLAZA); the domes, spires, bowl goblets and path rocks share a finer one (ROCKS), smaller ones more often (WEIGHT over the piece's reach), kept clear of the plazas (PieceLayer).
 * Nothing stands on the game's own ground round a copy of the zone (Zone.surrounds), since the game has none out there.
 */
public class UltraDesertFeature extends CellFeature {
    private static final String ZONE = "desert_284";
    private static final int PIECES = 41;
    private static final int CLEARANCE = 6;
    private static final int WEIGHT = 60;
    private static final double GAME_GROUND = 0.02;

    private static final class Layers {
        static final PieceLayer PLAZA = layer(1, 1, 35, 384, 0x91A2A5L, (ground, x, z, reach) -> !onGameGround(x, z, reach));
        static final PieceLayer ROCKS = layer(2, PIECES, 55, 56, 0xD0C25L,
                (ground, x, z, reach) -> !onGameGround(x, z, reach) && !nearPlaza(ground, x, z, reach + CLEARANCE));

        static PieceLayer layer(int first, int last, int chance, int minCell, long salt, PieceLayer.Fits fits) {
            return new PieceLayer(IntStream.rangeClosed(first, last).mapToObj(i -> Zone.get(ZONE + "_rock" + i))
                    .map(shape -> new PieceLayer.Kind(shape, Math.max(1, WEIGHT / Math.max(1, shape.reach())))).toList(),
                    chance, minCell, salt, fits);
        }
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        Ground ground = Ground.of(level, chunkGenerator);
        ChunkPos chunk = new ChunkPos(origin);
        Layers.PLAZA.place(level, chunk, ground);
        Layers.ROCKS.place(level, chunk, ground);
        return true;
    }

    private static boolean onGameGround(int x, int z, int r) {
        Zone zone = Zone.get(ZONE);
        for (int[] d : PieceLayer.spread(r)) {
            if (zone.surrounds(x + d[0], z + d[1]) > GAME_GROUND) return true;
        }
        return false;
    }

    private static boolean nearPlaza(Ground ground, int x, int z, int r) {
        int cell = Layers.PLAZA.cell;
        for (int cx = Math.floorDiv(x, cell) - 1; cx <= Math.floorDiv(x, cell) + 1; cx++) {
            for (int cz = Math.floorDiv(z, cell) - 1; cz <= Math.floorDiv(z, cell) + 1; cz++) {
                PieceLayer.Piece plaza = Layers.PLAZA.piece(ground, cx, cz);
                if (plaza == null) continue;
                for (int[] d : PieceLayer.spread(r)) {
                    if (plaza.covers(x + d[0], z + d[1], 3)) return true;
                }
            }
        }
        return false;
    }
}
