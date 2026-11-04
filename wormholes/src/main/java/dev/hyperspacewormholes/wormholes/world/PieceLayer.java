package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One layer of a world's Zone pieces (UltraPlantFeature's towers, UltraDesertFeature's formations): at most one piece
 * per cell from the world seed, chance in 100, picked by weight and turned at random, at a spot that keeps its reach
 * inside the cell, standing a block into the lowest ground of nine points across it, wherever fits lets it. Each chunk
 * builds its own part of every piece that reaches it.
 */
final class PieceLayer {
    record Kind(Zone shape, int weight) {}

    record Piece(Kind kind, int x, int y, int z, Rotation rotation) {
        Zone shape() {
            return kind.shape();
        }

        boolean covers(int wx, int wz, int pad) {
            return shape().distanceTo(x, z, rotation, wx, wz, pad) <= pad;
        }

        boolean touches(ChunkPos chunk) {
            int reach = shape().reach() + 1;
            return x + reach >= chunk.getMinBlockX() && x - reach <= chunk.getMaxBlockX()
                    && z + reach >= chunk.getMinBlockZ() && z - reach <= chunk.getMaxBlockZ();
        }
    }

    interface Fits {
        boolean test(CellFeature.Ground ground, int x, int z, int reach);
    }

    final int cell;
    private final List<Kind> kinds;
    private final int chance, reach;
    private final long salt;
    private final Fits fits;
    private final Map<Long, Optional<Piece>> pieces = CellFeature.cache(64);

    PieceLayer(List<Kind> kinds, int chance, int minCell, long salt, Fits fits) {
        this.kinds = kinds;
        this.chance = chance;
        this.salt = salt;
        this.fits = fits;
        reach = kinds.stream().mapToInt(k -> k.shape().reach()).max().orElse(32) + 2;
        cell = Math.max(minCell, 2 * reach + 8);
    }

    void place(WorldGenLevel level, ChunkPos chunk, CellFeature.Ground ground) {
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ();
        for (int cx = Math.floorDiv(x0 - reach, cell); cx <= Math.floorDiv(x0 + 15 + reach, cell); cx++) {
            for (int cz = Math.floorDiv(z0 - reach, cell); cz <= Math.floorDiv(z0 + 15 + reach, cell); cz++) {
                Piece piece = piece(ground, cx, cz);
                if (piece != null && piece.touches(chunk)) {
                    piece.shape().placeTurned(level, chunk, piece.x(), piece.y(), piece.z(), piece.rotation());
                }
            }
        }
    }

    /** Whether a piece in the cells round x, z covers it within pad. */
    boolean covered(CellFeature.Ground ground, int x, int z, int pad) {
        for (int cx = Math.floorDiv(x, cell) - 1; cx <= Math.floorDiv(x, cell) + 1; cx++) {
            for (int cz = Math.floorDiv(z, cell) - 1; cz <= Math.floorDiv(z, cell) + 1; cz++) {
                Piece piece = piece(ground, cx, cz);
                if (piece != null && piece.covers(x, z, pad)) return true;
            }
        }
        return false;
    }

    @Nullable Piece piece(CellFeature.Ground ground, int cx, int cz) {
        long key = ground.seed() ^ ChunkPos.asLong(cx, cz);
        synchronized (pieces) {
            Optional<Piece> known = pieces.get(key);
            if (known != null) return known.orElse(null);
        }
        RandomSource random = CellFeature.random(ground.seed(), cx, cz, salt);
        Piece piece = null;
        if (random.nextInt(100) < chance) {
            Kind kind = pick(random);
            int reach = kind.shape().reach();
            int margin = reach + 2, room = cell - 2 * margin;
            int x = cell * cx + (room > 0 ? margin + random.nextInt(room) : cell / 2);
            int z = cell * cz + (room > 0 ? margin + random.nextInt(room) : cell / 2);
            Rotation rotation = Rotation.values()[random.nextInt(4)];
            if (fits.test(ground, x, z, reach)) piece = new Piece(kind, x, ground.lowest(x, z, reach) - 1, z, rotation);
        }
        synchronized (pieces) {
            pieces.put(key, Optional.ofNullable(piece));
        }
        return piece;
    }

    private Kind pick(RandomSource random) {
        int roll = random.nextInt(kinds.stream().mapToInt(Kind::weight).sum());
        for (Kind k : kinds) {
            roll -= k.weight();
            if (roll < 0) return k;
        }
        return kinds.get(0);
    }

    /** The middle, four sides and four diagonals of a circle of radius r. */
    static int[][] spread(int r) {
        int h = r * 7 / 10;
        return new int[][]{{0, 0}, {-r, 0}, {r, 0}, {0, -r}, {0, r}, {-h, -h}, {h, -h}, {-h, h}, {h, h}};
    }
}
