package dev.hyperspacewormholes.wormholes.world;

import dev.hyperspacewormholes.wormholes.LuminescentIce;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Luminescent Ice lumps in caves under tundra biomes, placed like Cobblemon's type gems (placed feature luminescent_ice,
 * UNDERGROUND_ORES step). A try takes if its spot is deepslate or tuff touching air; that block stands in for Cobblemon's
 * core and the lump grows from it by Cobblemon's own rules (TypeGemCoreBlock.expand, 100 initial cycles, config defaults).
 * The buds are only kept in memory (there is no ice cluster block), so only the ice is placed: typically 17-22 blocks,
 * standing 2-4 blocks out of the wall, floor or ceiling, never in rock and never floating.
 */
public class LuminescentIceFeature extends CellFeature {
    /** Cobblemon's typeGemInitialExpansionCycles, typeGemGrowthChance and typeGemMaxConnected defaults. */
    private static final int CYCLES = 100;
    private static final float BUD_CHANCE = 0.1f;
    private static final int MAX_CONNECTED = 16;
    /** Keeps every block well inside the chunks a feature may write to; Cobblemon's growth never gets this far. */
    private static final int REACH = 8;
    private static final Direction[] DIRECTIONS = Direction.values();

    private enum Growth { DECORATION, FULL, STUNTED }

    private static final class Bud {
        final Direction facing;
        final Growth growth;
        int stage;

        Bud(Direction facing, Growth growth) {
            this.facing = facing;
            this.growth = growth;
        }
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        if (!level.getBlockState(origin).is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) return false;
        List<BlockPos> open = new ArrayList<>();
        for (Direction d : DIRECTIONS) {
            BlockPos pos = origin.relative(d);
            if (air(level, pos)) open.add(pos);
        }
        if (open.isEmpty()) return false;

        Set<BlockPos> ice = new LinkedHashSet<>();
        ice.add(open.get(random.nextInt(open.size())));
        Map<BlockPos, Bud> buds = new HashMap<>();
        for (int cycle = 0; cycle < CYCLES; cycle++) {
            for (BlockPos pos : lump(origin, ice)) {
                if (!pos.equals(origin)) sprout(level, origin, pos, ice, buds, random);
                ripen(origin, pos, ice, buds);
            }
        }

        BlockState state = LuminescentIce.BLOCK.defaultBlockState();
        for (BlockPos pos : ice) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        return true;
    }

    /** The rock spot, then the ice joined to it nearest first, like Cobblemon's getConnectedGemBlocks from the core. */
    private static List<BlockPos> lump(BlockPos origin, Set<BlockPos> ice) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        out.add(origin);
        seen.add(origin);
        queue.add(origin);
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            for (Direction d : DIRECTIONS) {
                BlockPos next = at.relative(d);
                if (ice.contains(next) && seen.add(next)) {
                    out.add(next);
                    queue.add(next);
                }
            }
        }
        return out;
    }

    /** Cobblemon's addClustersToBlock: a bud on each free air face 1 time in 10. */
    private static void sprout(WorldGenLevel level, BlockPos origin, BlockPos pos, Set<BlockPos> ice,
                               Map<BlockPos, Bud> buds, RandomSource random) {
        for (Direction d : DIRECTIONS) {
            if (random.nextFloat() >= BUD_CHANCE) continue;
            Growth growth = growth(ice.size(), random);
            BlockPos next = pos.relative(d);
            if (ice.contains(next) || buds.containsKey(next) || !near(origin, next) || !air(level, next)) continue;
            buds.put(next, new Bud(d, growth));
        }
    }

    /** Cobblemon's decideClusterGrowthType: decoration, full and stunted at 1:2:1, no full ones past 16 blocks. */
    private static Growth growth(int connected, RandomSource random) {
        if (connected > MAX_CONNECTED) return random.nextBoolean() ? Growth.DECORATION : Growth.STUNTED;
        float roll = random.nextFloat();
        return roll < 0.25f ? Growth.DECORATION : roll < 0.75f ? Growth.FULL : Growth.STUNTED;
    }

    /** Cobblemon's forced growClusters and advanceGrowth: every bud touching this block moves on a stage. */
    private static void ripen(BlockPos origin, BlockPos pos, Set<BlockPos> ice, Map<BlockPos, Bud> buds) {
        for (Direction d : DIRECTIONS) {
            BlockPos at = pos.relative(d);
            Bud bud = buds.get(at);
            if (bud == null || bud.growth == Growth.DECORATION || bud.growth == Growth.STUNTED && bud.stage == 3) continue;
            BlockPos support = at.relative(bud.facing.getOpposite());
            if (!ice.contains(support) && !support.equals(origin)) continue;
            if (bud.stage < 3) {
                bud.stage++;
            } else {
                buds.remove(at);
                ice.add(at);
            }
        }
    }

    private static boolean near(BlockPos origin, BlockPos pos) {
        return Math.abs(pos.getX() - origin.getX()) <= REACH && Math.abs(pos.getY() - origin.getY()) <= REACH
                && Math.abs(pos.getZ() - origin.getZ()) <= REACH;
    }

    private static boolean air(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.AIR) || state.is(Blocks.CAVE_AIR);
    }
}
