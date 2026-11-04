package dev.hyperspacewormholes.hyperspace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Puts the way back out of Hyperspace flat against a wall on the island, using the Overworld spatial distortions' wall
 * rule: a sturdy 3x3 face (no leaves) with a clear 3 wide, 2 deep, 3 high space in front of it on solid dry ground.
 * Every column at least INSET blocks from the edge is tried facing all four ways; faces with at least ROOMY blocks of
 * island floor ahead (a strip 3 wide and AHEAD long, within STEP blocks of the spot's height) win, then more floor,
 * then nearest Zones' arrival. If none fits, a small earth bank of natural ground is raised behind Zones' edge portal
 * spot, else Zones' free-standing spot stays. You arrive just inside the portal's plane (Trips.STEP_IN), facing out.
 */
public class IslandWall {
    private static final int HEIGHT = 3;
    private static final int INSET = 4;
    private static final int REACH = 44;
    private static final int AHEAD = 12;
    private static final int ROOMY = 5;
    private static final int STEP = 2;
    private static final double GAP = 0.3;
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private record Wall(BlockPos spot, Direction facing) {
    }

    public static void place(ServerLevel level, BlockPos centre, int slot, Zones.Zone zone) {
        if (zone.nx == 0 && zone.nz == 0) return;
        Wall wall = find(level, centre, slot, zone.ax, zone.az);
        if (wall == null) wall = raise(level, zone);
        if (wall != null) {
            Vec3 normal = new Vec3(wall.facing().getStepX(), 0, wall.facing().getStepZ());
            Vec3 base = Vec3.atBottomCenterOf(wall.spot()).add(normal.scale(GAP - 0.5));
            zone.rx = base.x;
            zone.ry = base.y;
            zone.rz = base.z;
            zone.nx = normal.x;
            zone.nz = normal.z;
        }
        zone.ax = zone.rx + zone.nx * Trips.STEP_IN;
        zone.ay = zone.ry;
        zone.az = zone.rz + zone.nz * Trips.STEP_IN;
        zone.yaw = (float) Math.toDegrees(Math.atan2(-zone.nx, zone.nz));
    }

    private static Wall find(ServerLevel level, BlockPos centre, int slot, double nearX, double nearZ) {
        Wall best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                if (!onIsland(centre, slot, dx, dz, INSET)) continue;
                int x = centre.getX() + dx, z = centre.getZ() + dz;
                double near = (x + 0.5 - nearX) * (x + 0.5 - nearX) + (z + 0.5 - nearZ) * (z + 0.5 - nearZ);
                int ground = surface(level, x, z);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    if (surface(level, x - facing.getStepX(), z - facing.getStepZ()) < ground + HEIGHT) continue;
                    BlockPos spot = new BlockPos(x, ground, z);
                    if (!isWall(level, spot, facing)) continue;
                    int floor = ahead(level, centre, slot, spot, facing);
                    double score = (floor >= ROOMY ? 0 : 1.0E9) - floor * 1.0E5 + near;
                    if (score >= bestScore) continue;
                    best = new Wall(spot, facing);
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private static int ahead(ServerLevel level, BlockPos centre, int slot, BlockPos spot, Direction facing) {
        Direction side = facing.getClockWise();
        int columns = 0;
        for (int out = 1; out <= AHEAD; out++) {
            for (int across = -1; across <= 1; across++) {
                BlockPos column = spot.relative(facing, out).relative(side, across);
                if (!onIsland(centre, slot, column.getX() - centre.getX(), column.getZ() - centre.getZ(), 1)) continue;
                if (Math.abs(surface(level, column.getX(), column.getZ()) - spot.getY()) <= STEP) columns++;
            }
        }
        return columns / 3;
    }

    private static boolean onIsland(BlockPos centre, int slot, int dx, int dz, int inset) {
        return Math.sqrt(dx * dx + dz * dz) <= Zones.edge(slot, Math.atan2(dz, dx)) - inset;
    }

    private static int surface(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static boolean isWall(ServerLevel level, BlockPos spot, Direction facing) {
        Direction side = facing.getClockWise();
        for (int ahead = 0; ahead <= 1; ahead++) {
            BlockPos below = spot.relative(facing, ahead).below();
            BlockState floor = level.getBlockState(below);
            if (!floor.getFluidState().isEmpty() || floor.is(BlockTags.LEAVES) || !floor.isFaceSturdy(level, below, Direction.UP)) return false;
        }
        for (int across = -1; across <= 1; across++) {
            for (int up = 0; up < HEIGHT; up++) {
                BlockPos back = spot.relative(side, across).above(up).relative(facing.getOpposite());
                BlockState block = level.getBlockState(back);
                if (block.is(BlockTags.LEAVES) || !block.isFaceSturdy(level, back, facing)) return false;
            }
        }
        return roomy(level, spot, facing);
    }

    private static boolean roomy(ServerLevel level, BlockPos spot, Direction facing) {
        Direction side = facing.getClockWise();
        AABB room = AABB.encapsulatingFullBlocks(spot.relative(side, -1),
                spot.relative(side, 1).relative(facing).above(HEIGHT - 1)).deflate(1.0E-3);
        return level.noCollision(room) && !level.containsAnyLiquid(room);
    }

    private static Wall raise(ServerLevel level, Zones.Zone zone) {
        Direction facing = Direction.getNearest(zone.nx, 0, zone.nz);
        BlockPos spot = BlockPos.containing(zone.rx, zone.ry, zone.rz);
        BlockState top = natural(level, spot.below()), fill = natural(level, spot.below(2));
        if (top == null) top = fill;
        if (fill == null) fill = top;
        if (top == null) return null;
        BlockState floor = level.getBlockState(spot.below());
        if (!floor.isFaceSturdy(level, spot.below(), Direction.UP) || !roomy(level, spot, facing)) return null;
        Direction side = facing.getClockWise();
        for (int across = -1; across <= 1; across++) {
            for (int back = 1; back <= 2; back++) {
                BlockPos column = spot.relative(side, across).relative(facing.getOpposite(), back);
                for (int down = 1; down <= 6 && level.getBlockState(column.below(down)).canBeReplaced(); down++) {
                    level.setBlock(column.below(down), fill, QUIET);
                }
                for (int up = 0; up < HEIGHT; up++) {
                    BlockPos pos = column.above(up);
                    if (level.getBlockState(pos).canBeReplaced()) level.setBlock(pos, up == HEIGHT - 1 ? top : fill, QUIET);
                }
            }
        }
        return isWall(level, spot, facing) ? new Wall(spot, facing) : null;
    }

    private static BlockState natural(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isCollisionShapeFullBlock(level, pos) || state.hasBlockEntity()) return null;
        boolean ground = state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.GRAVEL) || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.CLAY);
        return ground ? state : null;
    }
}
