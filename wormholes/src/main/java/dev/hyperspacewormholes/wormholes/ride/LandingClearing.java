package dev.hyperspacewormholes.wormholes.ride;

import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.ride.WormholeDestinations.Arrival;
import dev.hyperspacewormholes.wormholes.world.SwoleTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Clears the spot where a tunnel wormhole drops you off. Wild plants and whole trees around it go, anything someone
 * built stays.
 */
final class LandingClearing {
    private static final int RADIUS = 14;
    private static final int BELOW = 2;
    private static final int ABOVE = 30;
    private static final int JUNGLE_ABOVE = 110;
    private static final int MAX_TREE_LOGS = 12_000;
    private static final int LEAF_REACH = 6;
    private static final int TREE_CHECK_LOGS = 64;

    private LandingClearing() {}

    static Arrival clear(Arrival arrival) {
        ServerLevel level = arrival.level();
        BlockPos centre = BlockPos.containing(arrival.pos());
        if (!UltraPlantLightning.isUltraPlant(level)) {
            if (SwoleTree.isWood(level.getBlockState(centre.below()))) return arrival;
            clearAround(level, centre);
        }
        return new Arrival(level, WormholeDestinations.groundBelow(level, arrival.pos()), arrival.yaw(), arrival.plan());
    }

    private static void clearAround(ServerLevel level, BlockPos centre) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        boolean jungle = level.dimension() == Wormholes.ULTRA_JUNGLE;
        int fromX = jungle ? (Math.round(centre.getX() / 16f) - 1) * 16 - centre.getX() : -RADIUS, toX = jungle ? fromX + 31 : RADIUS;
        int fromZ = jungle ? (Math.round(centre.getZ() / 16f) - 1) * 16 - centre.getZ() : -RADIUS, toZ = jungle ? fromZ + 31 : RADIUS;
        int above = jungle ? JUNGLE_ABOVE : ABOVE;
        List<BlockPos> cutLogs = new ArrayList<>();
        List<BlockPos> cutLeaves = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = fromX; dx <= toX; dx++) {
            for (int dz = fromZ; dz <= toZ; dz++) {
                if (!jungle && dx * dx + dz * dz > RADIUS * RADIUS) continue;
                int x = centre.getX() + dx, z = centre.getZ() + dz;
                int terrainTop = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
                for (int y = Math.max(terrainTop, centre.getY() - BELOW); y <= centre.getY() + above; y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir() || !state.getFluidState().isEmpty() && !state.hasProperty(BlockStateProperties.WATERLOGGED)) continue;
                    if (state.is(BlockTags.LOGS)) {
                        if (!isTreeLog(level, pos.immutable())) continue;
                        cutLogs.add(pos.immutable());
                    } else if (isNaturalLeaves(state)) {
                        cutLeaves.add(pos.immutable());
                    } else if (state.is(BlockTags.LEAVES) || isBuilt(level, pos, state)) {
                        continue;
                    }
                    level.setBlock(pos, emptyBlock(level), jungle ? Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE : Block.UPDATE_ALL);
                }
            }
        }
        removeWholeTrees(level, cutLogs, cutLeaves);
    }

    private static void removeWholeTrees(ServerLevel level, List<BlockPos> cutLogs, List<BlockPos> cutLeaves) {
        if (cutLogs.isEmpty() && cutLeaves.isEmpty()) return;

        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> logQueue = new ArrayDeque<>();
        for (BlockPos cut : cutLogs) {
            seen.add(cut);
            for (BlockPos n : BlockPos.betweenClosed(cut.offset(-1, -1, -1), cut.offset(1, 1, 1))) {
                if (seen.add(n.immutable()) && level.getBlockState(n).is(BlockTags.LOGS)) logQueue.add(n.immutable());
            }
        }
        ArrayDeque<BlockPos> leafQueue = new ArrayDeque<>(cutLeaves);
        Map<BlockPos, Integer> leafDepth = new HashMap<>();
        cutLeaves.forEach(p -> leafDepth.put(p, 0));
        while (!leafQueue.isEmpty()) {
            BlockPos p = leafQueue.poll();
            int depth = leafDepth.get(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                BlockState state = level.getBlockState(n);
                if (state.is(BlockTags.LOGS)) {
                    if (seen.add(n)) logQueue.add(n);
                } else if (depth < LEAF_REACH && isNaturalLeaves(state) && !leafDepth.containsKey(n)) {
                    leafDepth.put(n, depth + 1);
                    leafQueue.add(n);
                }
            }
        }

        List<BlockPos> felled = new ArrayList<>(cutLogs);
        int count = 0;
        while (!logQueue.isEmpty() && count < MAX_TREE_LOGS) {
            BlockPos p = logQueue.poll();
            if (!level.getBlockState(p).is(BlockTags.LOGS)) continue;
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            felled.add(p);
            count++;
            for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                if (seen.add(n.immutable()) && level.getBlockState(n).is(BlockTags.LOGS)) logQueue.add(n.immutable());
            }
        }

        Set<BlockPos> canopy = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(felled);
        queue.addAll(cutLeaves);
        Map<BlockPos, Integer> depthOf = new HashMap<>();
        queue.forEach(p -> depthOf.put(p, 0));
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            int depth = depthOf.get(p);
            if (depth >= LEAF_REACH) continue;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (depthOf.containsKey(n) || !isNaturalLeaves(level.getBlockState(n))) continue;
                depthOf.put(n, depth + 1);
                canopy.add(n);
                queue.add(n);
            }
        }
        if (canopy.isEmpty()) return;
        BoundingBox around = BoundingBox.encapsulatingPositions(canopy).orElseThrow().inflatedBy(LEAF_REACH);
        List<BlockPos> standing = BlockPos.betweenClosedStream(around)
                .filter(p -> level.getBlockState(p).is(BlockTags.LOGS)).map(BlockPos::immutable).toList();
        for (BlockPos leaf : canopy) {
            if (standing.stream().noneMatch(log -> leaf.distManhattan(log) <= LEAF_REACH)) {
                level.setBlock(leaf, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static boolean isNaturalLeaves(BlockState state) {
        return state.is(BlockTags.LEAVES)
                && !(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT));
    }

    private static boolean isTreeLog(ServerLevel level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>(Set.of(start));
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(seen);
        while (!queue.isEmpty() && seen.size() <= TREE_CHECK_LOGS) {
            BlockPos p = queue.poll();
            for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                BlockState state = level.getBlockState(n);
                if (isNaturalLeaves(state)) return true;
                if (state.is(BlockTags.LOGS) && seen.add(n.immutable())) queue.add(n.immutable());
            }
        }
        return false;
    }

    static boolean isBuilt(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.hasBlockEntity()) return true;
        if (state.is(BlockTags.PLANKS) || state.is(BlockTags.STAIRS) || state.is(BlockTags.SLABS)
                || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.WOOL)
                || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.CROPS) || state.is(BlockTags.RAILS)
                || state.is(BlockTags.BUTTONS) || state.is(BlockTags.PRESSURE_PLATES) || state.is(BlockTags.CANDLES)
                || state.is(BlockTags.IMPERMEABLE) || state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.LANTERN)
                || state.is(Blocks.GLASS_PANE) || state.is(Blocks.LADDER) || state.is(Blocks.SCAFFOLDING)) {
            return true;
        }
        return level.structureManager().getStructureWithPieceAt(pos, structure -> true).isValid();
    }

    static boolean isClearableGrowth(BlockState state) {
        return isNaturalLeaves(state) || !state.isAir() && state.getFluidState().isEmpty()
                && (state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(Blocks.CHORUS_PLANT) || state.is(Blocks.CHORUS_FLOWER));
    }

    static boolean isVegetation(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.is(BlockTags.WART_BLOCKS) || state.is(BlockTags.CAVE_VINES)
                || state.is(Blocks.BAMBOO) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.SHROOMLIGHT)
                || state.is(Blocks.CRIMSON_FUNGUS) || state.is(Blocks.WARPED_FUNGUS)
                || state.is(Blocks.WEEPING_VINES) || state.is(Blocks.WEEPING_VINES_PLANT)
                || state.is(Blocks.TWISTING_VINES) || state.is(Blocks.TWISTING_VINES_PLANT)
                || state.is(Blocks.CHORUS_PLANT) || state.is(Blocks.CHORUS_FLOWER)
                || state.is(Blocks.AZALEA) || state.is(Blocks.FLOWERING_AZALEA) || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON);
    }

    static BlockState emptyBlock(Level level) {
        return UltraWarpRules.isUnderwater(level) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }
}
