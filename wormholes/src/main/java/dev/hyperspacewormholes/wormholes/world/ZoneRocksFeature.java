package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A zone's own rocks (Zone files NAME_rockN, cut from the zone by tools/zones/rocks.py) spread thick across its world: one in every CELL-block square, at a hashed spot with a hashed rock and quarter turn, standing on none of the zone's own columns (Zone.clearOf).
 * A square whose rock would land on the zone tries up to ATTEMPTS other spots, rocks and turns, so they crowd in round the playable area too.
 * Each sits in a pit like the playable area's rocks: its plain level goes a block under the lowest ground of five points across it, the land within 2 or 3 blocks (hashed) is dug down to that, and beyond it the ground climbs back a block every STEP blocks. Every chunk a rock or pit reaches builds its own part, all pits before any rock, and each square's pick is kept (picked) since some 50 chunks ask for it.
 */
public class ZoneRocksFeature extends CellFeature {
    private static final int CELL = 18, REACH = 24, ATTEMPTS = 6, STEP = 2, PIT = 22, SKIN = 4, SCAN_FROM = 76;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private final String zone;
    private final List<String> rocks;
    private final Map<Long, Optional<Rock>> picked = new ConcurrentHashMap<>();

    private record Rock(Zone shape, int x, int z, int ground, Rotation rotation, int margin) {}

    public ZoneRocksFeature(String zone, List<String> rocks) {
        this.zone = zone;
        this.rocks = rocks;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        ChunkPos chunk = new ChunkPos(origin);
        RandomState noise = level.getLevel().getChunkSource().randomState();
        Zone home = Zone.get(zone);
        int far = REACH + PIT;
        int x0 = Math.floorDiv(chunk.getMinBlockX() - far, CELL), x1 = Math.floorDiv(chunk.getMaxBlockX() + far, CELL);
        int z0 = Math.floorDiv(chunk.getMinBlockZ() - far, CELL), z1 = Math.floorDiv(chunk.getMaxBlockZ() + far, CELL);
        List<Rock> near = new ArrayList<>();
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                Rock rock = picked(level, chunkGenerator, noise, home, cx, cz);
                if (rock != null && reaches(rock, chunk, rock.shape().reach() + PIT)) near.add(rock);
            }
        }
        for (Rock rock : near) dig(level, chunk, rock);
        for (Rock rock : near) {
            if (reaches(rock, chunk, rock.shape().reach())) {
                rock.shape().placeTurned(level, chunk, rock.x(), rock.ground(), rock.z(), rock.rotation(), true);
            }
        }
        return true;
    }

    private @Nullable Rock picked(WorldGenLevel level, ChunkGenerator generator, RandomState noise, Zone home, int cx, int cz) {
        long cell = cellHash(level.getSeed(), cx, cz, 0x20C4);
        if (picked.size() > 100_000) picked.clear();
        return picked.computeIfAbsent(cell, key -> Optional.ofNullable(pick(level, generator, noise, home, cx, cz, cell))).orElse(null);
    }

    private @Nullable Rock pick(WorldGenLevel level, ChunkGenerator generator, RandomState noise, Zone home, int cx, int cz,
                                long cell) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            long h = attempt == 0 ? cell : mix(cell + 97L * attempt);
            int x = cx * CELL + (int) (unit(mix(h + 1)) * CELL);
            int z = cz * CELL + (int) (unit(mix(h + 2)) * CELL);
            Zone shape = Zone.get(rocks.get((int) (unit(mix(h + 3)) * rocks.size())));
            Rotation rotation = Rotation.values()[(int) (unit(mix(h + 4)) * 4)];
            int reach = shape.reach();
            Zone copy = home.near(x, z);
            if (copy.overlaps(x, z, reach) && !shape.clearOf(copy, x, z, rotation)) continue;
            int ground = Integer.MAX_VALUE;
            for (int[] at : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                ground = Math.min(ground, generator.getBaseHeight(x + at[0] * reach / 2, z + at[1] * reach / 2,
                        Heightmap.Types.WORLD_SURFACE_WG, level, noise) - 1);
            }
            return new Rock(shape, x, z, ground - 1, rotation, 2 + (int) (unit(mix(h + 5)) * 2));
        }
        return null;
    }

    private static boolean reaches(Rock rock, ChunkPos chunk, int reach) {
        return rock.x() + reach >= chunk.getMinBlockX() && rock.x() - reach <= chunk.getMaxBlockX()
                && rock.z() + reach >= chunk.getMinBlockZ() && rock.z() - reach <= chunk.getMaxBlockZ();
    }

    private static void dig(WorldGenLevel level, ChunkPos chunk, Rock rock) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState[] skin = new BlockState[SKIN];
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                float distance = rock.shape().distanceTo(rock.x(), rock.z(), rock.rotation(), x, z, PIT);
                if (distance > PIT) continue;
                int floor = rock.ground() + Math.max(0, (int) Math.ceil((distance - rock.margin()) / STEP));
                int top = SCAN_FROM;
                while (top > floor && level.getBlockState(pos.set(x, top, z)).isAir()) top--;
                if (top <= floor) continue;
                for (int i = 0; i < SKIN; i++) skin[i] = level.getBlockState(pos.set(x, top - i, z));
                for (int y = top; y > floor; y--) level.setBlock(pos.set(x, y, z), AIR, Block.UPDATE_CLIENTS);
                for (int i = 0; i < SKIN; i++) level.setBlock(pos.set(x, floor - i, z), skin[i], Block.UPDATE_CLIENTS);
            }
        }
    }
}
