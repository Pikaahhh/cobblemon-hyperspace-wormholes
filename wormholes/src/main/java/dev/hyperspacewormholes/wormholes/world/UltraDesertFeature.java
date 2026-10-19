package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The Ultra Desert's rock formations covered in water gems: gem totems, stacked pillars and arches.
 */
public class UltraDesertFeature extends CellFeature {
    private static final int EMPTY_CHANCE = 20;

    private static final BlockState ROCK = Blocks.CALCITE.defaultBlockState();
    private static final BlockState ACCENT = Blocks.SMOOTH_SANDSTONE.defaultBlockState();

    public UltraDesertFeature() {
        super(56, 16, 40, 0x0DE5E47L);
    }

    @Override
    protected @Nullable Map<BlockPos, BlockState> shape(RandomSource random, int x, int z, Ground ground) {
        if (random.nextInt(100) < EMPTY_CHANCE || !ground.dry(x, z)) return null;
        Map<BlockPos, BlockState> rock = new HashMap<>();
        int y = ground.at(x, z);
        Function<BlockPos, BlockState> stone = p -> (mix(p) & 7) == 0 ? ACCENT : ROCK;
        int kind = random.nextInt(100);
        if (kind < 45) totem(random, new Vec3(x + 0.5, y, z + 0.5), ground, stone, rock);
        else if (kind < 75) pillar(random, new Vec3(x + 0.5, y, z + 0.5), stone, rock);
        else arch(random, x, z, ground, stone, rock);
        Sculpt.inlay(rock, 8 + random.nextInt(8), 1.6, gem(), random);
        crystals(random, x, z, ground, rock);
        return rock;
    }

    @Override
    protected boolean replaces(BlockState existing, BlockState placed) {
        return placed.is(cluster().getBlock()) ? existing.isAir() : !existing.is(Blocks.BEDROCK);
    }

    private static void totem(RandomSource random, Vec3 foot, Ground ground, Function<BlockPos, BlockState> stone, Map<BlockPos, BlockState> into) {
        double head = 5 + random.nextDouble() * 3;
        double legs = 9 + random.nextDouble() * 7;
        double yaw = random.nextDouble() * Math.PI * 2;
        Vec3 centre = foot.add(0, legs + head, 0);
        int count = 2 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            double angle = yaw + i * Math.PI * 2 / count + (random.nextDouble() - 0.5) * 0.5;
            double spread = 4 + random.nextDouble() * 3;
            int fx = Mth.floor(foot.x + Math.cos(angle) * spread), fz = Mth.floor(foot.z + Math.sin(angle) * spread);
            Vec3 feet = new Vec3(fx + 0.5, ground.at(fx, fz) - 2, fz + 0.5);
            Vec3 top = centre.add(Math.cos(angle) * head * 0.3, -head * 0.6, Math.sin(angle) * head * 0.3);
            Vec3 knee = feet.lerp(top, 0.5).add(Math.cos(angle) * 2, 0, Math.sin(angle) * 2);
            Sculpt.tube(feet, knee, 2.2, 1.7, stone, into);
            Sculpt.tube(knee, top, 1.7, 1.5, stone, into);
        }
        Sculpt.facets(centre, head, yaw, stone, into);
        Sculpt.cone(centre.add(0, head * 0.8, 0), 1.6, 4 + random.nextDouble() * 4, 0.3, stone, into);
    }

    private static void pillar(RandomSource random, Vec3 foot, Function<BlockPos, BlockState> stone, Map<BlockPos, BlockState> into) {
        Vec3 at = foot.add(0, -2, 0);
        int segments = 2 + random.nextInt(3);
        for (int i = 0; i < segments; i++) {
            double r = 3.2 + random.nextDouble() * 1.8 - i * 0.3;
            double h = 4 + random.nextDouble() * 3;
            barrel(at, r, h, stone, into);
            at = at.add(random.nextDouble() - 0.5, h, random.nextDouble() - 0.5);
        }
        double cap = 3.5 + random.nextDouble() * 1.5;
        Sculpt.facets(at.add(0, cap * 0.6, 0), cap, random.nextDouble() * Math.PI * 2, stone, into);
        Sculpt.cone(at.add(0, cap * 1.4, 0), 1.2, 3 + random.nextDouble() * 3, 0.3, stone, into);
    }

    private static void barrel(Vec3 base, double r, double h, Function<BlockPos, BlockState> stone, Map<BlockPos, BlockState> into) {
        for (int y = 0; y <= Mth.ceil(h); y++) {
            double t = y / h;
            double radius = r * (0.8 + 0.2 * Math.sin(t * Math.PI));
            int reach = Mth.ceil(radius);
            for (int x = -reach; x <= reach; x++) {
                for (int z = -reach; z <= reach; z++) {
                    if (x * x + z * z > radius * radius) continue;
                    BlockPos p = BlockPos.containing(base.x + x, base.y + y, base.z + z);
                    into.put(p, stone.apply(p));
                }
            }
        }
    }

    private static void arch(RandomSource random, int x, int z, Ground ground, Function<BlockPos, BlockState> stone, Map<BlockPos, BlockState> into) {
        double angle = random.nextDouble() * Math.PI * 2;
        double half = 7 + random.nextDouble() * 5;
        double rise = 10 + random.nextDouble() * 7;
        double thick = 2.4 + random.nextDouble() * 1.2;
        int ax = Mth.floor(x - Math.cos(angle) * half), az = Mth.floor(z - Math.sin(angle) * half);
        int bx = Mth.floor(x + Math.cos(angle) * half), bz = Mth.floor(z + Math.sin(angle) * half);
        Vec3 a = new Vec3(ax + 0.5, ground.at(ax, az) - 2, az + 0.5);
        Vec3 b = new Vec3(bx + 0.5, ground.at(bx, bz) - 2, bz + 0.5);
        Sculpt.arch(a, b, rise, 24, 1.0, t -> thick * (1.25 - 0.35 * Math.sin(t * Math.PI)), stone, into);
        Sculpt.ball(a.add(0, 2, 0), thick * 1.6, stone, into);
        Sculpt.ball(b.add(0, 2, 0), thick * 1.6, stone, into);
    }

    private static void crystals(RandomSource random, int x, int z, Ground ground, Map<BlockPos, BlockState> into) {
        int count = 4 + random.nextInt(8);
        for (int i = 0; i < count; i++) {
            int cx = x + random.nextInt(29) - 14, cz = z + random.nextInt(29) - 14;
            into.putIfAbsent(new BlockPos(cx, ground.at(cx, cz), cz), cluster());
        }
    }

    static BlockState gem() {
        return cobblemon("water_gem_block", Blocks.DIAMOND_BLOCK);
    }

    static BlockState cluster() {
        return cobblemon("water_gem_cluster", Blocks.AMETHYST_CLUSTER);
    }

    private static BlockState cobblemon(String name, Block fallback) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobblemon", name);
        return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.get(id).defaultBlockState() : fallback.defaultBlockState();
    }

    private static long mix(BlockPos p) {
        long h = p.asLong() * 0x9E3779B97F4A7C15L;
        return h ^ (h >>> 29);
    }
}
