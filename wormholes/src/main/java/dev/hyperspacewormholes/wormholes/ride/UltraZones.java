package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.api.riding.RidingStyle;
import com.cobblemon.mod.common.api.riding.behaviour.ActiveRidingContext;
import com.cobblemon.mod.common.api.riding.behaviour.RidingController;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.world.Zone;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Ultra worlds that are playable zones (see Zone): the four Ultra Space Wilds, the Ultra Jungle, Deep Sea, Crater,
 * Plant, Desert, Megalopolis and Ruin. An invisible wall keeps everyone but spectators inside a zone's walk mask and below
 * its ceiling (UltraZoneWallMixin), and the way home is the only way out. Arrivals land at the copy nearest where the
 * rider set off.
 */
public final class UltraZones {
    private static final Map<ResourceKey<Level>, String> ZONES = Map.ofEntries(Map.entry(Wormholes.WILDS_CLIFF, "wilds_289"),
            Map.entry(Wormholes.WILDS_CRAG, "wilds_290"), Map.entry(Wormholes.WILDS_CAVE, "wilds_291"),
            Map.entry(Wormholes.WILDS_WATER, "wilds_292"), Map.entry(Wormholes.ULTRA_JUNGLE, "jungle_286"),
            Map.entry(Wormholes.ULTRA_DEEP_SEA, "deep_sea_277"), Map.entry(Wormholes.ULTRA_CRATER, "crater_283"),
            Map.entry(Wormholes.ULTRA_PLANT, "plant_282"), Map.entry(Wormholes.ULTRA_DESERT, "desert_284"),
            Map.entry(Wormholes.ULTRA_MEGALOPOLIS, "megalo_281"), Map.entry(Wormholes.ULTRA_RUIN, "ruin_288"));
    public static final List<ResourceKey<Level>> WILDS = List.of(Wormholes.WILDS_CLIFF, Wormholes.WILDS_CRAG,
            Wormholes.WILDS_CAVE, Wormholes.WILDS_WATER);
    public static final List<ResourceKey<Level>> ZONE_ONLY = List.of(Wormholes.WILDS_CLIFF, Wormholes.WILDS_CRAG,
            Wormholes.WILDS_CAVE, Wormholes.WILDS_WATER, Wormholes.ULTRA_DEEP_SEA, Wormholes.ULTRA_MEGALOPOLIS, Wormholes.ULTRA_RUIN);
    private static final Map<UUID, Stood> LAST_INSIDE = new HashMap<>();

    record Stood(ResourceKey<Level> dimension, Vec3 pos) {}

    private UltraZones() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    public static boolean isSky(Level level) {
        return level.dimension() == Wormholes.WILDS_CLIFF;
    }

    public static boolean isZone(Level level) {
        return ZONES.containsKey(level.dimension());
    }

    /** The Ultra worlds and the ride between them. */
    public static boolean isUltraSpace(Level level) {
        return isZone(level) || level.dimension() == Wormholes.ULTRA_WARP;
    }

    public static boolean walled(Entity entity) {
        return entity instanceof Player player ? !player.isSpectator()
                : entity.hasPassenger(passenger -> passenger instanceof Player player && !player.isSpectator());
    }

    public static @Nullable Zone of(@Nullable Level level) {
        String name = level == null ? null : ZONES.get(level.dimension());
        return name == null ? null : Zone.get(name);
    }

    /**
     * Whether x, z is where nobody may fly in the Ultra Ruin: the street round the fallen beam, and the stair and ledge up
     * to it, with room enough that nobody can jump off in the air and drift onto them. The walk mask already leaves the
     * beam as the only way over to Guzzlord.
     */
    public static boolean noFlying(Level level, double x, double z) {
        if (level.dimension() != Wormholes.ULTRA_RUIN) return false;
        Zone zone = of(level).near(x, z);
        double beamX = x - zone.arrival().getX() + 68, beamZ = z - zone.arrival().getZ() + 210;
        return beamZ >= 104 && beamZ < (beamX >= 51 ? 177 : 123);
    }

    /** Zone.allows, and a Pokemon that is being flown is kept out of noFlying too. */
    public static boolean allows(Zone zone, Entity entity, double x, double y, double z) {
        return zone.allows(x, y, z) && !(flown(entity) && noFlying(entity.level(), x, z));
    }

    private static boolean flown(Entity entity) {
        if (!(entity instanceof PokemonEntity pokemon)) return false;
        RidingController controller = pokemon.getRidingController();
        ActiveRidingContext context = controller == null ? null : controller.getContext();
        return context != null && context.getStyle() == RidingStyle.AIR;
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
