package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Ultra Plant's dark rock monoliths, the coal cables strung between them and the smaller crags.
 */
public class UltraPlantFeature extends Feature<NoneFeatureConfiguration> {
    private static final int CELL = 90;
    private static final int CELL_MARGIN = 32;
    private static final int MONOLITH_CHANCE = 75;
    private static final int MIN_HEIGHT = 30;
    private static final int MAX_HEIGHT = 70;
    private static final int MIN_RADIUS = 10;
    private static final int MAX_RADIUS = 16;
    private static final double TAPER = 0.35;
    private static final int CRAG_CELL = 16;
    private static final int CRAG_MARGIN = 3;
    private static final int CRAG_CHANCE = 30;
    private static final int CRAG_MIN_HEIGHT = 4;
    private static final int CRAG_MAX_HEIGHT = 16;
    private static final int ROOT = 8;
    private static final int SPECKLE_CHANCE = 15;
    private static final int CABLE_STRAIGHT_CHANCE = 40;
    private static final int CABLE_DIAGONAL_CHANCE = 15;
    private static final double CABLE_RADIUS = 1.3;
    private static final double TIE_RADIUS = 2.4;
    private static final int TIE_STEP = 14;
    private static final double SAG = 0.12;
    private static final Map<Long, Optional<Monolith>> CACHE = Collections.synchronizedMap(CellFeature.cache(4096));

    private static final BlockState[] ROCK = {Blocks.BLACKSTONE.defaultBlockState(),
            Blocks.SMOOTH_BASALT.defaultBlockState(), Blocks.BASALT.defaultBlockState()};
    private static final BlockState CABLE = Blocks.COAL_BLOCK.defaultBlockState();
    private static final BlockState TIE = Blocks.POLISHED_ANDESITE.defaultBlockState();

    private record Monolith(int x, int z, int half, int ground, int height, long shape) {
        int anchorY() {
            return ground + (int) (height * 0.65);
        }

        int anchorRadius() {
            return taper(half, anchorY() - ground, height);
        }
    }

    private record Chunk(WorldGenLevel level, int x0, int z0, BlockPos.MutableBlockPos pos) {
        boolean near(int x, int z, int reach) {
            return x + reach >= x0 && x - reach <= x0 + 15 && z + reach >= z0 && z - reach <= z0 + 15;
        }

        void set(int x, int y, int z, BlockState state, boolean replaceSolid) {
            if (x < x0 || x > x0 + 15 || z < z0 || z > z0 + 15) return;
            if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return;
            pos.set(x, y, z);
            if (replaceSolid || level.getBlockState(pos).isAir()) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    public UltraPlantFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        CellFeature.Ground ground = CellFeature.Ground.of(context);
        int x0 = (context.origin().getX() >> 4) << 4, z0 = (context.origin().getZ() >> 4) << 4;
        Chunk chunk = new Chunk(context.level(), x0, z0, new BlockPos.MutableBlockPos());
        for (int cx = Math.floorDiv(x0, CELL) - 2; cx <= Math.floorDiv(x0 + 15, CELL) + 2; cx++) {
            for (int cz = Math.floorDiv(z0, CELL) - 2; cz <= Math.floorDiv(z0 + 15, CELL) + 2; cz++) {
                Monolith here = monolith(ground, cx, cz, false);
                if (here == null) continue;
                buildMonolith(chunk, here, true);
                cable(chunk, ground, here, cx, cz, cx + 1, cz, CABLE_STRAIGHT_CHANCE);
                cable(chunk, ground, here, cx, cz, cx, cz + 1, CABLE_STRAIGHT_CHANCE);
                cable(chunk, ground, here, cx, cz, cx + 1, cz + 1, CABLE_DIAGONAL_CHANCE);
            }
        }
        for (int cx = Math.floorDiv(x0, CRAG_CELL) - 1; cx <= Math.floorDiv(x0 + 15, CRAG_CELL) + 1; cx++) {
            for (int cz = Math.floorDiv(z0, CRAG_CELL) - 1; cz <= Math.floorDiv(z0 + 15, CRAG_CELL) + 1; cz++) {
                Monolith crag = monolith(ground, cx, cz, true);
                if (crag != null) buildMonolith(chunk, crag, false);
            }
        }
        return true;
    }

    private static long hash(long seed, int a, int b, int salt) {
        return CellFeature.mix(seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL)
                ^ (salt * 0x165667B19E3779F9L));
    }

    private static int roll(long seed, int a, int b, int salt, int bound) {
        return (int) Math.floorMod(hash(seed, a, b, salt), (long) bound);
    }

    private static @Nullable Monolith monolith(CellFeature.Ground ground, int cx, int cz, boolean crag) {
        long key = hash(ground.seed(), cx, cz, crag ? 20 : 0);
        Optional<Monolith> known = CACHE.get(key);
        if (known == null) {
            known = Optional.ofNullable(findMonolith(ground, cx, cz, crag));
            CACHE.put(key, known);
        }
        return known.orElse(null);
    }

    private static @Nullable Monolith findMonolith(CellFeature.Ground ground, int cx, int cz, boolean crag) {
        long seed = ground.seed();
        int s = crag ? 20 : 0;
        int cell = crag ? CRAG_CELL : CELL, margin = crag ? CRAG_MARGIN : CELL_MARGIN;
        int minHeight = crag ? CRAG_MIN_HEIGHT : MIN_HEIGHT, maxHeight = crag ? CRAG_MAX_HEIGHT : MAX_HEIGHT;
        if (roll(seed, cx, cz, s + 1, 100) >= (crag ? CRAG_CHANCE : MONOLITH_CHANCE)) return null;
        int x = cx * cell + margin + roll(seed, cx, cz, s + 2, cell - 2 * margin);
        int z = cz * cell + margin + roll(seed, cx, cz, s + 3, cell - 2 * margin);
        int half = crag ? 1 + roll(seed, cx, cz, s + 4, 2) : MIN_RADIUS + roll(seed, cx, cz, s + 4, MAX_RADIUS - MIN_RADIUS + 1);
        int height = minHeight + roll(seed, cx, cz, s + 5, maxHeight - minHeight + 1);
        int low = Integer.MAX_VALUE, r = 2 * half + 3;
        for (int[] c : new int[][]{{0, 0}, {-r, -r}, {r, -r}, {-r, r}, {r, r}}) {
            low = Math.min(low, ground.at(x + c[0], z + c[1]));
        }
        return new Monolith(x, z, half, low, height, hash(seed, cx, cz, s + 7));
    }

    private static void buildMonolith(Chunk chunk, Monolith m, boolean big) {
        if (!chunk.near(m.x, m.z, 2 * m.half + 4)) return;
        column(chunk, m, 0, m.x, m.z, m.half, m.height);
        int others = big ? 2 + roll(m.shape, 0, 0, 1, 4) : 1 + roll(m.shape, 0, 0, 1, 2);
        for (int i = 1; i <= others; i++) {
            double angle = roll(m.shape, i, 0, 2, 360) * Math.PI / 180;
            int distance = Math.max(1, m.half * (40 + roll(m.shape, i, 0, 3, 71)) / 100);
            int radius = Math.max(1, m.half * (30 + roll(m.shape, i, 0, 4, 41)) / 100);
            int height = Math.max(3, m.height * (25 + roll(m.shape, i, 0, 5, 66)) / 100);
            column(chunk, m, i, m.x + (int) Math.round(Math.cos(angle) * distance),
                    m.z + (int) Math.round(Math.sin(angle) * distance), radius, height);
        }
    }

    private static int taper(int radius, int dy, int height) {
        if (dy <= 0) return radius;
        return (int) Math.round(radius * (1 - TAPER * dy / (double) height));
    }

    private static void column(Chunk chunk, Monolith m, int i, int cx, int cz, int radius, int height) {
        int rock = roll(m.shape, i, 0, 6, ROCK.length);
        for (int dy = -ROOT; dy < height; dy++) {
            int y = m.ground + dy;
            int r = taper(radius, dy, height);
            int fromX = Math.max(-r, chunk.x0 - cx), toX = Math.min(r, chunk.x0 + 15 - cx);
            int fromZ = Math.max(-r, chunk.z0 - cz), toZ = Math.min(r, chunk.z0 + 15 - cz);
            for (int dx = fromX; dx <= toX; dx++) {
                for (int dz = fromZ; dz <= toZ; dz++) {
                    if (dx * dx + dz * dz > r * r + r) continue;
                    int x = cx + dx, z = cz + dz;
                    boolean speckle = roll(m.shape ^ x, y, z, 8, 100) < SPECKLE_CHANCE;
                    chunk.set(x, y, z, ROCK[speckle ? (rock + 1) % ROCK.length : rock], true);
                }
            }
        }
    }

    private static void cable(Chunk chunk, CellFeature.Ground ground, Monolith a, int ax, int az, int bx, int bz, int chance) {
        if (roll(ground.seed(), ax * 31 + bx, az * 31 + bz, 6, 100) >= chance) return;
        Monolith b = monolith(ground, bx, bz, false);
        if (b == null) return;
        Vec3 across = new Vec3(b.x - a.x, 0, b.z - a.z);
        if (across.lengthSqr() < 1) return;
        Vec3 dir = across.normalize();
        Vec3 from = new Vec3(a.x + 0.5 + dir.x * (a.anchorRadius() - 2), a.anchorY() + 0.5, a.z + 0.5 + dir.z * (a.anchorRadius() - 2));
        Vec3 to = new Vec3(b.x + 0.5 - dir.x * (b.anchorRadius() - 2), b.anchorY() + 0.5, b.z + 0.5 - dir.z * (b.anchorRadius() - 2));
        double length = from.distanceTo(to);
        if (length < 4) return;
        int midGround = ground.at(Mth.floor((from.x + to.x) / 2), Mth.floor((from.z + to.z) / 2));
        double sag = Mth.clamp(length * SAG, 0, Math.min(from.y, to.y) - midGround - 5);

        double minX = Math.min(from.x, to.x) - 3, maxX = Math.max(from.x, to.x) + 3;
        double minZ = Math.min(from.z, to.z) - 3, maxZ = Math.max(from.z, to.z) + 3;
        if (maxX < chunk.x0 || minX > chunk.x0 + 16 || maxZ < chunk.z0 || minZ > chunk.z0 + 16) return;

        int samples = (int) Math.ceil(length * 2);
        List<Vec3> points = new ArrayList<>(samples + 1);
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            points.add(from.lerp(to, t).subtract(0, sag * 4 * t * (1 - t), 0));
        }
        for (Vec3 p : points) sphere(chunk, p, CABLE_RADIUS, CABLE);
        for (int i = TIE_STEP; i <= samples - TIE_STEP / 2; i += TIE_STEP) tie(chunk, points, i);
    }

    private static void tie(Chunk chunk, List<Vec3> points, int i) {
        Vec3 p = points.get(i);
        Vec3 along = points.get(Math.min(points.size() - 1, i + 1)).subtract(points.get(Math.max(0, i - 1))).normalize();
        int r = Mth.ceil(TIE_RADIUS);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    Vec3 offset = new Vec3(dx, dy, dz);
                    double d = offset.length();
                    if (d <= CABLE_RADIUS || d > TIE_RADIUS || Math.abs(offset.dot(along)) > 0.6) continue;
                    chunk.set(Mth.floor(p.x + dx), Mth.floor(p.y + dy), Mth.floor(p.z + dz), TIE, false);
                }
            }
        }
    }

    private static void sphere(Chunk chunk, Vec3 centre, double radius, BlockState state) {
        int r = Mth.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    chunk.set(Mth.floor(centre.x + dx), Mth.floor(centre.y + dy), Mth.floor(centre.z + dz), state, false);
                }
            }
        }
    }
}
