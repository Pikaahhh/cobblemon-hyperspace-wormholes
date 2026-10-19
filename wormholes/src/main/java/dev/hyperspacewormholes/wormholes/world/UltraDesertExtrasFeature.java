package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The smaller stuff on the Ultra Desert: calcite pools spilling water, water gem beds and gem boulders.
 */
public class UltraDesertExtrasFeature extends CellFeature {
    private static final int POOL_CHANCE = 15;
    private static final int GEM_BED_CHANCE = 30;
    private static final int BOULDER_CHANCE = 15;
    private static final int LIFT_MIN = 5;
    private static final int LIFT_MAX = 7;
    private static final int POOL_DEPTH = 2;
    private static final double STAND_BASE = 0.3;
    private static final double STAND_FLARE = 1.9;

    private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState SPECKLE = Blocks.SMOOTH_SANDSTONE.defaultBlockState();

    public UltraDesertExtrasFeature() {
        super(30, 6, 20, 0xE7A5L);
    }

    @Override
    protected @Nullable Map<BlockPos, BlockState> shape(RandomSource random, int x, int z, Ground ground) {
        if (!ground.dry(x, z)) return null;
        int roll = random.nextInt(100);
        Map<BlockPos, BlockState> into = new HashMap<>();
        if (roll < POOL_CHANCE) pool(random, x, z, ground, into);
        else if (roll < POOL_CHANCE + GEM_BED_CHANCE) gemBed(random, x, z, ground, into);
        else if (roll < POOL_CHANCE + GEM_BED_CHANCE + BOULDER_CHANCE) boulder(random, x, z, ground, into);
        return into.isEmpty() ? null : into;
    }

    @Override
    protected boolean replaces(BlockState existing, BlockState placed) {
        return placed.is(UltraDesertFeature.cluster().getBlock()) ? existing.isAir() : !existing.is(Blocks.BEDROCK);
    }

    @Override
    protected void placed(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.WATER)) level.scheduleTick(pos, Fluids.WATER, 1);
    }

    private static void pool(RandomSource random, int x, int z, Ground ground, Map<BlockPos, BlockState> into) {
        int high = Integer.MIN_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) high = Math.max(high, ground.at(x + dx, z + dz));
        }
        int water = Math.max(ground.at(x, z) - 1, high - 1) + LIFT_MIN + random.nextInt(LIFT_MAX - LIFT_MIN + 1);
        int floor = water - POOL_DEPTH;
        int side = random.nextInt(4);
        int notchX = side == 0 ? 2 : side == 1 ? -2 : 0, notchZ = side == 2 ? 2 : side == 3 ? -2 : 0;
        int deep = random.nextInt(4);
        int deepX = deep == 0 ? 1 : deep == 1 ? -1 : 0, deepZ = deep == 2 ? 1 : deep == 3 ? -1 : 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                int px = x + dx, pz = z + dz;
                long h = hash(px, pz);
                int foot = ground.at(px, pz) - 2;
                double d = Math.sqrt(dx * dx + dz * dz);
                for (int y = foot; y < floor; y++) {
                    double t = (y - foot) / (double) Math.max(1, floor - foot);
                    if (d > STAND_BASE + STAND_FLARE * t * t * t + ((h & 7) - 3.5) * 0.04) continue;
                    into.put(new BlockPos(px, y, pz), (h & 7) == 0 ? SPECKLE : CALCITE);
                }
                boolean inside = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                boolean deepHere = dx == 0 && dz == 0 || dx == deepX && dz == deepZ;
                boolean notch = dx == notchX && dz == notchZ;
                for (int y = floor; y <= water + 1; y++) {
                    BlockPos p = new BlockPos(px, y, pz);
                    if (y == floor) into.put(p, CALCITE);
                    else if (inside && y == floor + 1 && !deepHere) into.put(p, CALCITE);
                    else if (inside) into.put(p, y <= water ? WATER : AIR);
                    else if (notch) into.put(p, y < water ? CALCITE : y == water ? WATER : AIR);
                    else into.put(p, CALCITE);
                }
            }
        }
    }

    private static void gemBed(RandomSource random, int x, int z, Ground ground, Map<BlockPos, BlockState> into) {
        BlockState gem = UltraDesertFeature.gem();
        BlockState cluster = UltraDesertFeature.cluster();
        double r = 3 + random.nextDouble() * 4;
        double phase = random.nextDouble() * Math.PI * 2;
        int reach = Mth.ceil(r * 1.3);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double angle = Math.atan2(dz, dx);
                double edge = r * (1 + 0.25 * Math.sin(3 * angle + phase) + 0.12 * Math.sin(7 * angle - phase));
                if (dx * dx + dz * dz > edge * edge) continue;
                int px = x + dx, pz = z + dz;
                int top = ground.at(px, pz) - 1;
                for (int y = top - 1; y <= top + 1; y++) into.put(new BlockPos(px, y, pz), gem);
                if ((hash(px, pz) >>> 8) % 5 < 2) into.put(new BlockPos(px, top + 2, pz), cluster);
            }
        }
    }

    private static void boulder(RandomSource random, int x, int z, Ground ground, Map<BlockPos, BlockState> into) {
        double r = 2.4 + random.nextDouble() * 1.8;
        Vec3 centre = new Vec3(x + 0.5, ground.at(x, z) + r * 0.7, z + 0.5);
        Function<BlockPos, BlockState> rock = p -> (hash(p.getX() * 31 + p.getY(), p.getZ()) & 7) == 0 ? SPECKLE : CALCITE;
        Map<BlockPos, BlockState> lump = new HashMap<>();
        Sculpt.facets(centre, r, random.nextDouble() * Math.PI * 2, rock, lump);
        for (int i = 0; i < 3; i++) {
            Vec3 bump = centre.add((random.nextDouble() - 0.5) * r, (random.nextDouble() - 0.3) * r, (random.nextDouble() - 0.5) * r);
            Sculpt.ball(bump, r * 0.6, rock, lump);
        }
        Sculpt.inlay(lump, 6 + random.nextInt(4), Math.max(1.8, r * 0.7), UltraDesertFeature.gem(), random);
        into.putAll(lump);
    }
}
