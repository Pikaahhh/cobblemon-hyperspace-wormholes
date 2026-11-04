package dev.hyperspacewormholes.wormholes.ride;

import dev.hyperspacewormholes.wormholes.ride.WormholeDestinations.Arrival;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
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

/** Clears the spot where a tunnel wormhole drops you off: wild plants and whole trees go, anything built stays. */
final class LandingClearing {
    private static final int RADIUS = 14;
    private static final int BELOW = 2;
    private static final int ABOVE = 30;
    private static final int MAX_TREE_LOGS = 12_000;
    private static final int LEAF_REACH = 6;
    private static final int TREE_CHECK_LOGS = 64;
    private static final List<TagKey<Block>> BUILT_TAGS = List.of(BlockTags.PLANKS, BlockTags.STAIRS, BlockTags.SLABS,
            BlockTags.WALLS, BlockTags.FENCES, BlockTags.FENCE_GATES, BlockTags.DOORS, BlockTags.TRAPDOORS, BlockTags.WOOL,
            BlockTags.WOOL_CARPETS, BlockTags.CROPS, BlockTags.RAILS, BlockTags.BUTTONS, BlockTags.PRESSURE_PLATES,
            BlockTags.CANDLES, BlockTags.IMPERMEABLE);
    private static final Set<Block> BUILT_BLOCKS = Set.of(Blocks.FARMLAND, Blocks.DIRT_PATH, Blocks.TORCH,
            Blocks.WALL_TORCH, Blocks.LANTERN, Blocks.GLASS_PANE, Blocks.LADDER, Blocks.SCAFFOLDING);
    private static final List<TagKey<Block>> VEGETATION_TAGS = List.of(BlockTags.LEAVES, BlockTags.LOGS, BlockTags.FLOWERS,
            BlockTags.SAPLINGS, BlockTags.REPLACEABLE_BY_TREES, BlockTags.WART_BLOCKS, BlockTags.CAVE_VINES);
    private static final Set<Block> VEGETATION_BLOCKS = Set.of(Blocks.BAMBOO, Blocks.SUGAR_CANE, Blocks.CACTUS,
            Blocks.SWEET_BERRY_BUSH, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM_BLOCK,
            Blocks.RED_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM, Blocks.SHROOMLIGHT, Blocks.CRIMSON_FUNGUS, Blocks.WARPED_FUNGUS,
            Blocks.WEEPING_VINES, Blocks.WEEPING_VINES_PLANT, Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT,
            Blocks.CHORUS_PLANT, Blocks.CHORUS_FLOWER, Blocks.AZALEA, Blocks.FLOWERING_AZALEA, Blocks.MOSS_CARPET,
            Blocks.PUMPKIN, Blocks.MELON);

    private LandingClearing() {}

    static Arrival clear(Arrival arrival) {
        ServerLevel level = arrival.level();
        BlockPos centre = BlockPos.containing(arrival.pos());
        if (UltraZones.isZone(level)) return arrival;
        if (!UltraPlantLightning.isUltraPlant(level)) clearAround(level, centre);
        return new Arrival(level, WormholeDestinations.groundBelow(level, arrival.pos()), arrival.yaw(), arrival.plan());
    }

    private static void clearAround(ServerLevel level, BlockPos centre) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        List<BlockPos> cutLogs = new ArrayList<>();
        List<BlockPos> cutLeaves = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                int x = centre.getX() + dx, z = centre.getZ() + dz;
                int terrainTop = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
                for (int y = Math.max(terrainTop, centre.getY() - BELOW); y <= centre.getY() + ABOVE; y++) {
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
                    level.setBlock(pos, emptyBlock(level), Block.UPDATE_ALL);
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
            queueLogsAround(level, cut, seen, logQueue);
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
            queueLogsAround(level, p, seen, logQueue);
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

    private static void queueLogsAround(ServerLevel level, BlockPos p, Set<BlockPos> seen, ArrayDeque<BlockPos> queue) {
        for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
            BlockPos at = n.immutable();
            if (seen.add(at) && level.getBlockState(at).is(BlockTags.LOGS)) queue.add(at);
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
        return state.hasBlockEntity() || isAny(state, BUILT_TAGS, BUILT_BLOCKS)
                || level.structureManager().getStructureWithPieceAt(pos, structure -> true).isValid();
    }

    private static boolean isAny(BlockState state, List<TagKey<Block>> tags, Set<Block> blocks) {
        if (blocks.contains(state.getBlock())) return true;
        for (TagKey<Block> tag : tags) {
            if (state.is(tag)) return true;
        }
        return false;
    }

    static boolean isClearableGrowth(BlockState state) {
        return isNaturalLeaves(state) || !state.isAir() && state.getFluidState().isEmpty()
                && (state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(Blocks.CHORUS_PLANT) || state.is(Blocks.CHORUS_FLOWER));
    }

    static boolean isVegetation(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && isAny(state, VEGETATION_TAGS, VEGETATION_BLOCKS);
    }

    static BlockState emptyBlock(Level level) {
        return UltraWarpRules.isUnderwater(level) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }
}
