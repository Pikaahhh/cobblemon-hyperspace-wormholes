package dev.hyperspacewormholes.wormholes.ride;

import dev.hyperspacewormholes.wormholes.Wormholes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Harmless lightning that strikes the open ground around players in the Ultra Plant, just for show. STATUE_SHARE of
 * the strikes go to the star of a statue Xurkitree within STRIKE_RANGE instead, when one stands there.
 */
public final class UltraPlantLightning {
    private static final int STRIKE_EVERY_TICKS = 70;
    private static final int STRIKE_RANGE = 72;
    private static final int FLOOR_TRIES = 8;
    private static final float STATUE_SHARE = 0.3f;

    private UltraPlantLightning() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(UltraPlantLightning::tick);
    }

    public static boolean isUltraPlant(Level level) {
        return level.dimension() == Wormholes.ULTRA_PLANT;
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(Wormholes.ULTRA_PLANT);
        if (level == null) return;
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (random.nextInt(STRIKE_EVERY_TICKS) != 0) continue;
            if (random.nextFloat() < STATUE_SHARE) {
                Vec3 star = PlantXurkitrees.statueStar(level, player.position(), STRIKE_RANGE, random);
                if (star != null) {
                    strike(level, star);
                    continue;
                }
            }
            for (int tries = 0; tries < FLOOR_TRIES; tries++) {
                int x = player.getBlockX() + random.nextInt(STRIKE_RANGE * 2 + 1) - STRIKE_RANGE;
                int z = player.getBlockZ() + random.nextInt(STRIKE_RANGE * 2 + 1) - STRIKE_RANGE;
                BlockPos column = new BlockPos(x, 0, z);
                if (!level.isLoaded(column)) continue;
                BlockPos target = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
                int floor = level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                        level, level.getChunkSource().randomState());
                if (target.getY() > floor + 1) continue;
                strike(level, Vec3.atBottomCenterOf(target));
                break;
            }
        }
    }

    private static void strike(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        bolt.moveTo(at);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }
}
