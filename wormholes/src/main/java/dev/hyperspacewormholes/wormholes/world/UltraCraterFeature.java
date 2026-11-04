package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The Ultra Crater, placed from block templates of the game's Ultra Crater (data/hyperspace_wormholes/crater_formations): the big yellow crater plateau with its green path, whole craters scattered over the plain round it, Celesteela launch towers (a red spire in a root mound), steaming root mounds and a few lone steam vents.
 * Plateaus and craters come from CraterLayout (one plateau per big cell, one crater per CRATER_CELL); towers and mounds go one per small cell, kept off the plateaus and craters (up to ATTEMPTS spots). Every small shape keeps its own reach in from its cell's edge, so two in neighbouring cells never meet.
 * All the dirt is an even mix of dirt and coarse dirt picked by a position hash, and each chunk builds its columns straight from the templates, cutting the plain down to a template's top and running its bottom row down to the plain so nothing floats or gets buried.
 */
public class UltraCraterFeature extends CellFeature {
    private static final String FOLDER = "crater_formations";
    private static final int CELL = 56;
    private static final int EMPTY_CHANCE = 10;
    private static final int ATTEMPTS = 6;
    private static final int VENT_CHANCE = 35;
    private static final int CARVE = 6;
    private static final int FOOT = 12;
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private static final byte EARTH = 1, RIDGE = 2, YELLOW = 3, GROOVE = 4, GREEN = 5, PATH = 6, DIRT_CELL = 7, SPIRE = 8,
            ROOT = 9, GLOW = 10, VENT = 11;

    private static final List<BlockTemplate> PLATEAUS = CraterLayout.PLATEAUS;
    private static final List<BlockTemplate> SHAPES = load("tower_tall", "tower", "tower_small", "tower_small_b",
            "vent_mound", "vent_mound_small");
    private static final int[] WEIGHTS = {8, 10, 7, 7, 4, 2};
    private static final int BIG_REACH = PLATEAUS.stream().mapToInt(BlockTemplate::reach).max().orElse(0) + 1;
    private static final int REACH = SHAPES.stream().mapToInt(BlockTemplate::reach).max().orElse(0) + 1;

    private static final BlockState PLAIN = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState MOUND = Blocks.BROWN_TERRACOTTA.defaultBlockState();
    private static final BlockState MOUND_DARK = Blocks.MUD_BRICKS.defaultBlockState();
    private static final BlockState SIDE = Blocks.YELLOW_TERRACOTTA.defaultBlockState();
    private static final BlockState SIDE_GROOVE = Blocks.TERRACOTTA.defaultBlockState();
    private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState[] LATTICE = {Blocks.RED_NETHER_BRICKS.defaultBlockState(),
            Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState()};
    private static final BlockState ROOT_BLOCK = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState ROOT_SMOOTH = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState LIGHT = Blocks.SHROOMLIGHT.defaultBlockState();

    private static final Map<Long, Placement> BIG = cache(64);
    private static final Map<Long, Placement> PIECES = cache(512);
    private static final Map<Long, Cell> SMALL = cache(512);
    private static final BlockState[] EARTH_MIX = {DIRT, PLAIN};

    private record Placement(BlockTemplate template, int x, int y, int z, int turns, boolean mirror, boolean plainEarth) {
        int column(int wx, int wz) {
            return template.column(wx, wz, x, z, turns, mirror);
        }

        byte at(int column, int ty) {
            return template.at(column / template.sz(), ty, column % template.sz());
        }

        boolean over(int wx, int wz) {
            int c = column(wx, wz);
            return c >= 0 && template.filled(c);
        }

        boolean touches(int x0, int z0) {
            int r = template.reach() + 1;
            return x0 + 15 >= x - r && x0 <= x + r && z0 + 15 >= z - r && z0 <= z + r;
        }
    }

    private record Cell(@Nullable Placement shape, @Nullable BlockPos vent) {}

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        Ground ground = Ground.of(level, chunkGenerator);
        CraterLayout layout = CraterLayout.of(ground.seed());
        int x0 = (origin.getX() >> 4) << 4, z0 = (origin.getZ() >> 4) << 4;
        List<Placement> here = new ArrayList<>();
        List<BlockPos> vents = new ArrayList<>();
        int big = CraterLayout.BIG_CELL;
        for (int cx = Math.floorDiv(x0 - BIG_REACH, big); cx <= Math.floorDiv(x0 + 15 + BIG_REACH, big); cx++) {
            for (int cz = Math.floorDiv(z0 - BIG_REACH, big); cz <= Math.floorDiv(z0 + 15 + BIG_REACH, big); cz++) {
                Placement p = big(ground, layout, cx, cz);
                if (p != null && p.touches(x0, z0)) here.add(p);
            }
        }
        int step = CraterLayout.CRATER_CELL, reach = CraterLayout.CRATER_REACH;
        for (int cx = Math.floorDiv(x0 - reach, step); cx <= Math.floorDiv(x0 + 15 + reach, step); cx++) {
            for (int cz = Math.floorDiv(z0 - reach, step); cz <= Math.floorDiv(z0 + 15 + reach, step); cz++) {
                Placement p = piece(ground, layout, cx, cz);
                if (p != null && p.touches(x0, z0)) here.add(p);
            }
        }
        for (int cx = Math.floorDiv(x0 - REACH, CELL); cx <= Math.floorDiv(x0 + 15 + REACH, CELL); cx++) {
            for (int cz = Math.floorDiv(z0 - REACH, CELL); cz <= Math.floorDiv(z0 + 15 + REACH, CELL); cz++) {
                Cell cell = small(ground, layout, cx, cz);
                if (cell.shape() != null && cell.shape().touches(x0, z0)) here.add(cell.shape());
                if (cell.vent() != null && cell.vent().getX() >> 4 == x0 >> 4 && cell.vent().getZ() >> 4 == z0 >> 4) vents.add(cell.vent());
            }
        }
        buildSlice(level, here, vents, x0, z0);
        return true;
    }

    private static void buildSlice(LevelAccessor level, List<Placement> here, List<BlockPos> vents, int x0, int z0) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] columns = new int[here.size()];
        int minY = level.getMinBuildHeight() + 1, maxY = level.getMaxBuildHeight() - 1;
        for (int wx = x0; wx < x0 + 16; wx++) {
            for (int wz = z0; wz < z0 + 16; wz++) {
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, wx, wz) - 1;
                boolean any = false;
                int top = Integer.MIN_VALUE;
                for (int i = 0; i < here.size(); i++) {
                    Placement p = here.get(i);
                    int c = p.column(wx, wz);
                    columns[i] = c;
                    if (c < 0) continue;
                    int ty = 0;
                    while (ty < p.template().sy() && p.at(c, ty) != BlockTemplate.AIR) ty++;
                    if (ty > 0) top = Math.max(top, p.y() + ty - 1 - p.template().oy());
                    any |= ty > 0 || p.over(wx, wz);
                }
                if (!any) {
                    plain(level, pos.set(wx, surface, wz), vents);
                    continue;
                }
                int placedTop = surface;
                if (top != Integer.MIN_VALUE && top < surface && surface - top <= CARVE) {
                    for (int y = top + 1; y <= surface; y++) level.setBlock(pos.set(wx, y, wz), Blocks.AIR.defaultBlockState(), FLAGS);
                }
                for (int i = 0; i < here.size(); i++) {
                    int c = columns[i];
                    if (c < 0) continue;
                    Placement p = here.get(i);
                    for (int ty = 0; ty < p.template().sy(); ty++) {
                        byte cell = p.at(c, ty);
                        if (cell == BlockTemplate.AIR) continue;
                        int y = p.y() + ty - p.template().oy();
                        if (y < minY || y > maxY) continue;
                        if (level.getBlockState(pos.set(wx, y, wz)).is(Blocks.BEDROCK)) continue;
                        BlockState state = block(cell, pos, p.plainEarth());
                        level.setBlock(pos, state, FLAGS);
                        placedTop = Math.max(placedTop, y);
                        if (ty == 0) {
                            for (int d = 1; d <= FOOT && y - d >= minY; d++) {
                                BlockState below = level.getBlockState(pos.set(wx, y - d, wz));
                                if (!below.isAir() && !below.canBeReplaced()) break;
                                level.setBlock(pos, state, FLAGS);
                            }
                        }
                    }
                }
                mixTop(level, pos.set(wx, placedTop, wz), placedTop);
            }
        }
    }

    private static void mixTop(LevelAccessor level, BlockPos.MutableBlockPos pos, int top) {
        for (int y = top; y >= top - CARVE; y--) {
            BlockState state = level.getBlockState(pos.setY(y));
            if (state.isAir()) continue;
            if (state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)) level.setBlock(pos, earth(pos), FLAGS);
            return;
        }
    }

    private static BlockState earth(BlockPos pos) {
        return EARTH_MIX[(int) Math.floorMod(mix(hash(pos.getX(), pos.getZ()) ^ 0xD1A7L), (long) EARTH_MIX.length)];
    }

    private static void plain(LevelAccessor level, BlockPos.MutableBlockPos pos, List<BlockPos> vents) {
        BlockState existing = level.getBlockState(pos);
        for (BlockPos vent : vents) {
            if (vent.getX() == pos.getX() && vent.getZ() == pos.getZ() && !existing.isAir() && existing.getFluidState().isEmpty()) {
                level.setBlock(pos, SteamVent.BLOCK.defaultBlockState(), FLAGS);
                return;
            }
        }
        if (existing.is(Blocks.PODZOL) || existing.is(Blocks.COARSE_DIRT)) level.setBlock(pos, earth(pos), FLAGS);
    }

    private static BlockState block(byte cell, BlockPos pos, boolean plainEarth) {
        long h = mix(pos.asLong());
        return switch (cell) {
            case EARTH -> plainEarth ? earth(pos) : MOUND;
            case RIDGE -> MOUND_DARK;
            case YELLOW -> SIDE;
            case GROOVE -> SIDE_GROOVE;
            case GREEN -> MOSS;
            case PATH -> MOSS;
            case DIRT_CELL -> DIRT;
            case SPIRE -> LATTICE[(int) Math.floorMod(h + Math.floorMod(pos.getY(), 4), (long) LATTICE.length)];
            case ROOT -> (h & 3) == 0 ? ROOT_SMOOTH : ROOT_BLOCK;
            case GLOW -> LIGHT;
            case VENT -> SteamVent.BLOCK.defaultBlockState();
            default -> MOUND;
        };
    }

    private static @Nullable Placement big(Ground ground, CraterLayout layout, int cx, int cz) {
        return placed(BIG, ground, layout.seed(), cx, cz, layout.plateau(cx, cz));
    }

    private static @Nullable Placement piece(Ground ground, CraterLayout layout, int cx, int cz) {
        return placed(PIECES, ground, layout.seed(), cx, cz, layout.crater(cx, cz));
    }

    private static @Nullable Placement placed(Map<Long, Placement> cache, Ground ground, long seed, int cx, int cz,
                                              @Nullable CraterLayout.Spot spot) {
        long key = seed ^ ChunkPos.asLong(cx, cz);
        synchronized (cache) {
            if (cache.containsKey(key)) return cache.get(key);
        }
        Placement p = null;
        if (spot != null) {
            BlockTemplate t = spot.template();
            p = new Placement(t, spot.x(), base(ground, spot.x(), spot.z(), t.reach()), spot.z(), spot.turns(), spot.mirror(), true);
        }
        synchronized (cache) {
            cache.put(key, p);
        }
        return p;
    }

    private static Cell small(Ground ground, CraterLayout layout, int cx, int cz) {
        long key = layout.seed() ^ ChunkPos.asLong(cx, cz);
        synchronized (SMALL) {
            Cell known = SMALL.get(key);
            if (known != null) return known;
        }
        RandomSource random = random(layout.seed(), cx, cz, 0x7A11E5L);
        Placement shape = null;
        if (random.nextInt(100) >= EMPTY_CHANCE) {
            BlockTemplate t = SHAPES.get(pick(random));
            int margin = t.reach() + 1, range = Math.max(1, CELL - 2 * margin);
            for (int attempt = 0; attempt < ATTEMPTS && shape == null; attempt++) {
                int x = cx * CELL + margin + random.nextInt(range);
                int z = cz * CELL + margin + random.nextInt(range);
                Placement p = new Placement(t, x, 0, z, random.nextInt(4), random.nextBoolean(), false);
                if (clear(layout, p)) shape = new Placement(t, x, base(ground, x, z, t.reach()), z, p.turns(), p.mirror(), false);
            }
        }
        BlockPos vent = null;
        if (random.nextInt(100) < VENT_CHANCE) {
            int vx = cx * CELL + 8 + random.nextInt(CELL - 16), vz = cz * CELL + 8 + random.nextInt(CELL - 16);
            if (layout.plateauDistance(vx, vz) > 2 && !layout.onCrater(vx, vz)) vent = new BlockPos(vx, 0, vz);
        }
        Cell cell = new Cell(shape, vent);
        synchronized (SMALL) {
            SMALL.put(key, cell);
        }
        return cell;
    }

    private static boolean clear(CraterLayout layout, Placement p) {
        int r = p.template().reach();
        for (int wx = p.x() - r; wx <= p.x() + r; wx += 2) {
            for (int wz = p.z() - r; wz <= p.z() + r; wz += 2) {
                if (!p.over(wx, wz)) continue;
                if (layout.plateauDistance(wx, wz) <= 2 || layout.onCrater(wx, wz)) return false;
            }
        }
        return true;
    }

    private static int pick(RandomSource random) {
        int total = 0;
        for (int w : WEIGHTS) total += w;
        int roll = random.nextInt(total);
        for (int i = 0; i < WEIGHTS.length; i++) {
            roll -= WEIGHTS[i];
            if (roll < 0) return i;
        }
        return 0;
    }

    private static int base(Ground ground, int x, int z, int reach) {
        int r = reach / 2;
        int sum = ground.at(x, z) + ground.at(x + r, z) + ground.at(x - r, z) + ground.at(x, z + r) + ground.at(x, z - r);
        return Math.round(sum / 5f) - 1;
    }

    private static List<BlockTemplate> load(String... names) {
        return Stream.of(names).map(n -> BlockTemplate.load(FOLDER, n)).toList();
    }
}
