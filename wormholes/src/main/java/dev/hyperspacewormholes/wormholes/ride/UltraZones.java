package dev.hyperspacewormholes.wormholes.ride;

import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.world.Zone;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Ultra worlds that are playable zones (see Zone): the four Ultra Space Wilds, the Ultra Jungle, Deep Sea, Crater,
 * Plant, Desert and Megalopolis. An invisible wall keeps everyone but spectators inside a zone's walk mask and below
 * its ceiling (UltraZoneWallMixin), and the way home is the only way out. Arrivals land at the copy nearest where the
 * rider set off.
 */
public final class UltraZones {
    private static final Map<ResourceKey<Level>, String> ZONES = Map.of(Wormholes.WILDS_CLIFF, "wilds_289",
            Wormholes.WILDS_CRAG, "wilds_290", Wormholes.WILDS_CAVE, "wilds_291", Wormholes.WILDS_WATER, "wilds_292",
            Wormholes.ULTRA_JUNGLE, "jungle_286", Wormholes.ULTRA_DEEP_SEA, "deep_sea_277",
            Wormholes.ULTRA_CRATER, "crater_283", Wormholes.ULTRA_PLANT, "plant_282", Wormholes.ULTRA_DESERT, "desert_284",
            Wormholes.ULTRA_MEGALOPOLIS, "megalo_281");
    public static final List<ResourceKey<Level>> WILDS = List.of(Wormholes.WILDS_CLIFF, Wormholes.WILDS_CRAG,
            Wormholes.WILDS_CAVE, Wormholes.WILDS_WATER);
    public static final List<ResourceKey<Level>> ZONE_ONLY = List.of(Wormholes.WILDS_CLIFF, Wormholes.WILDS_CRAG,
            Wormholes.WILDS_CAVE, Wormholes.WILDS_WATER, Wormholes.ULTRA_DEEP_SEA, Wormholes.ULTRA_MEGALOPOLIS);
    private static final Map<UUID, Stood> LAST_INSIDE = new HashMap<>();

    record Stood(ResourceKey<Level> dimension, Vec3 pos) {}

    private UltraZones() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(UltraZones::tick);
    }

    public static boolean isSky(Level level) {
        return level.dimension() == Wormholes.WILDS_CLIFF;
    }

    public static boolean isZone(Level level) {
        return ZONES.containsKey(level.dimension());
    }

    public static boolean walled(Entity entity) {
        return entity instanceof Player player ? !player.isSpectator()
                : entity.hasPassenger(passenger -> passenger instanceof Player player && !player.isSpectator());
    }

    public static @Nullable Zone of(@Nullable Level level) {
        String name = level == null ? null : ZONES.get(level.dimension());
        return name == null ? null : Zone.get(name);
    }

    private static void tick(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, String> entry : ZONES.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null || level.players().isEmpty()) continue;
            Zone zone = Zone.get(entry.getValue());
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator()) continue;
                Entity body = player.getRootVehicle();
                Vec3 pos = body.position();
                if (zone.allows(pos.x, pos.y, pos.z)) LAST_INSIDE.put(player.getUUID(), new Stood(level.dimension(), pos));
                else putBack(level, player, body, zone, LAST_INSIDE.get(player.getUUID()));
            }
        }
    }

    /**
     * Puts the player, or what they ride (body), back where they last stood, or at the nearest copy of the zone's
     * arrival if that was in another dimension or nowhere yet.
     */
    static void putBack(ServerLevel level, ServerPlayer player, Entity body, Zone zone, @Nullable Stood last) {
        Vec3 pos = body.position();
        Zone copy = zone.near(pos.x, pos.z);
        Vec3 back = last != null && last.dimension() == level.dimension() ? last.pos() : copy.standOn(copy.arrival());
        body.setDeltaMovement(Vec3.ZERO);
        if (body == player) player.connection.teleport(back.x, back.y, back.z, player.getYRot(), player.getXRot());
        else body.teleportTo(back.x, back.y, back.z);
        body.hurtMarked = true;
    }
}
