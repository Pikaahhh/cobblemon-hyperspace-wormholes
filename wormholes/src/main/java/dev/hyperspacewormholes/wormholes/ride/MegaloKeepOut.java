package dev.hyperspacewormholes.wormholes.ride;

import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.world.Zone;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps players off the round floor under the Megalo Tower's summit stage (zones/megalo_281). A player within RADIUS of
 * the spot's block centre and between FLOOR and UNDERSIDE below it is put back each tick where they last stood outside
 * it (or at the zone's arrival), with their mount. Spectators, creative players, Pokemon and a fight's party during its
 * scenes (NecrozmaFight.inScene) are left alone; each copy of the zone has its own floor.
 */
public final class MegaloKeepOut {
    private static final double RADIUS = 12.5, FLOOR = 11.25, UNDERSIDE = 3;
    private static final Map<UUID, UltraZones.Stood> LAST_OUTSIDE = new HashMap<>();

    private MegaloKeepOut() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(MegaloKeepOut::tick);
    }

    public static boolean inside(Zone zone, Vec3 pos) {
        BlockPos spot = zone.near(pos.x, pos.z).spot();
        if (spot == null || pos.y < spot.getY() - FLOOR || pos.y > spot.getY() - UNDERSIDE) return false;
        double dx = pos.x - (spot.getX() + 0.5), dz = pos.z - (spot.getZ() + 0.5);
        return dx * dx + dz * dz < RADIUS * RADIUS;
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(Wormholes.ULTRA_MEGALOPOLIS);
        if (level == null || level.players().isEmpty()) return;
        Zone zone = UltraZones.of(level);
        if (zone == null) return;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative()) {
                LAST_OUTSIDE.remove(player.getUUID());
                continue;
            }
            if (NecrozmaFight.inScene(player)) continue;
            Entity body = player.getRootVehicle();
            Vec3 pos = body.position();
            if (!inside(zone, pos) && !inside(zone, player.position())) {
                LAST_OUTSIDE.put(player.getUUID(), new UltraZones.Stood(level.dimension(), pos));
            } else {
                UltraZones.putBack(level, player, body, zone, LAST_OUTSIDE.get(player.getUUID()));
            }
        }
    }
}
