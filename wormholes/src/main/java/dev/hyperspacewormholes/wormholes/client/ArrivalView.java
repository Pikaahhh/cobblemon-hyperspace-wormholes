package dev.hyperspacewormholes.wormholes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Whether the world a ride comes out in is there to be seen, so the white doesn't clear over one still loading: the
 * chunks within CHUNKS of you have come and what's in front of you has been built (DRAWN of the ground's sections in
 * view, or all there is), or BUILD_TICKS have passed since. At most MOST_TICKS after the loading screen.
 */
final class ArrivalView {
    private static final int CHUNKS = 4, DRAWN_CHUNKS = 3;
    private static final int LEAST_TICKS = 2, BUILD_TICKS = 12, MOST_TICKS = 100;
    private static final float DRAWN = 0.75f;
    private static final int LEVEL = 24;

    private static int waited, loaded;

    private ArrivalView() {}

    static void reset() {
        waited = loaded = 0;
    }

    /** Once a tick with the loading screen gone. */
    static boolean ready(Minecraft mc) {
        if (++waited >= MOST_TICKS) return true;
        if (mc.level == null || mc.player == null || !chunksCame(mc)) {
            loaded = 0;
            return false;
        }
        return ++loaded >= BUILD_TICKS || loaded >= LEAST_TICKS && (mc.levelRenderer.hasRenderedAllSections() || drawn(mc));
    }

    private static boolean chunksCame(Minecraft mc) {
        ChunkPos at = mc.player.chunkPosition();
        int reach = Math.min(CHUNKS, mc.options.getEffectiveRenderDistance());
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx * dx + dz * dz <= reach * reach && !mc.level.hasChunk(at.x + dx, at.z + dz)) return false;
            }
        }
        return true;
    }

    private static boolean drawn(Minecraft mc) {
        BlockPos at = mc.player.blockPosition();
        float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD, ahead = Mth.cos(yaw), across = -Mth.sin(yaw);
        int seen = 0, built = 0;
        for (int dx = -DRAWN_CHUNKS; dx <= DRAWN_CHUNKS; dx++) {
            for (int dz = -DRAWN_CHUNKS; dz <= DRAWN_CHUNKS; dz++) {
                if (dx * dx + dz * dz > DRAWN_CHUNKS * DRAWN_CHUNKS || dx * across + dz * ahead < 0) continue;
                int x = at.getX() + dx * 16, z = at.getZ() + dz * 16;
                int y = Mth.clamp(mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, at.getY() - LEVEL, at.getY() + LEVEL);
                BlockPos pos = new BlockPos(x, y, z);
                if (mc.level.isOutsideBuildHeight(pos)) continue;
                seen++;
                if (mc.levelRenderer.isSectionCompiled(pos)) built++;
            }
        }
        return built >= seen * DRAWN;
    }
}
