package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * The Ultra Plant's canopy, balls of persistent dark oak leaves (tinted navy by the biome's foliage colour) on the floor's
 * top block, one to each SPACING square of a grid jittered by the world seed, RADIUS_MIN to RADIUS_MAX round, FLAT as tall
 * as wide and sunk SINK of their height into the floor. Leaves go into air only, so the domes wrap round the towers' feet
 * and the Xurkitrees' legs; each chunk builds its own part of every dome that reaches it.
 */
public class UltraPlantCanopyFeature extends CellFeature {
    private static final int SPACING = 7;
    private static final double RADIUS_MIN = 2.5, RADIUS_MAX = 4.2, FLAT = 1.0, SINK = 0.12, ROUGH = 0.1;
    private static final BlockState LEAVES = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        Ground ground = Ground.of(level, chunkGenerator);
        ChunkPos chunk = new ChunkPos(origin);
        int pad = Mth.ceil(RADIUS_MAX) + 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int gx = Math.floorDiv(chunk.getMinBlockX() - pad, SPACING); gx <= Math.floorDiv(chunk.getMaxBlockX() + pad, SPACING); gx++) {
            for (int gz = Math.floorDiv(chunk.getMinBlockZ() - pad, SPACING); gz <= Math.floorDiv(chunk.getMaxBlockZ() + pad, SPACING); gz++) {
                long h = mix(hash(gx, gz) ^ ground.seed());
                double cx = (gx + (h & 0xFF) / 255.0) * SPACING, cz = (gz + (h >>> 8 & 0xFF) / 255.0) * SPACING;
                double r = RADIUS_MIN + (h >>> 16 & 0xFF) / 255.0 * (RADIUS_MAX - RADIUS_MIN), rv = r * FLAT;
                int x0 = Math.max(chunk.getMinBlockX(), Mth.floor(cx - r)), x1 = Math.min(chunk.getMaxBlockX(), Mth.ceil(cx + r));
                int z0 = Math.max(chunk.getMinBlockZ(), Mth.floor(cz - r)), z1 = Math.min(chunk.getMaxBlockZ(), Mth.ceil(cz + r));
                if (x0 > x1 || z0 > z1) continue;
                double cy = ground.at(Mth.floor(cx), Mth.floor(cz)) - rv * SINK;
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int y = Mth.floor(cy - rv); y <= Mth.ceil(cy + rv); y++) {
                            double dx = (x + 0.5 - cx) / r, dy = (y + 0.5 - cy) / rv, dz = (z + 0.5 - cz) / r;
                            double rough = (mix(hash(x, z) ^ y * 0x632BE59BD9B4E019L) & 0xFFFF) / 65535.0;
                            if (dx * dx + dy * dy + dz * dz > 1 + ROUGH * (rough * 2 - 1)) continue;
                            pos.set(x, y, z);
                            if (level.getBlockState(pos).isAir()) level.setBlock(pos, LEAVES, 2);
                        }
                    }
                }
            }
        }
        return true;
    }
}
