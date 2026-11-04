package dev.hyperspacewormholes.hyperspace;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hyperspace trips: starting one through a portal, the time left on the action bar, and sending you back where you came
 * from when time's up, you die or you leave. Sent-out Pokemon are recalled before every teleport so Cobblemon can't crash
 * the server. You land just in front of the exit portal (STEP_IN), facing out of it; back home, if that portal has closed,
 * a Rift stands open behind you and shrinks shut after REMNANT ticks.
 * <p>
 * The trip, with the spot you went in at, is kept until it really ends, even if a /tp moves you to another dimension. If
 * the way home is gone you go to your respawn point or the world spawn. A Distortion Splicer trip shows "Time Remaining:
 * ???" and kicks you out hurt when the hidden time runs out (DistortionSplicer.collapse); a Hoopa Unbound one hands every
 * arrival to HoopaEvent.enter and has no legendary, Mega or level scaling.
 */
public class Trips {
    public static final double STEP_IN = 0.35;
    private static final int REMNANT = 30;

    private static final Map<UUID, Vec3> doors = new HashMap<>();
    private static final Set<UUID> fresh = new HashSet<>();
    private static final Map<Rift, Integer> remnants = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Trips::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) leave(player, false);
        });
    }

    private static void tick(MinecraftServer server) {
        remnants.entrySet().removeIf(entry -> {
            entry.setValue(entry.getValue() - 1);
            if (entry.getValue() > 0 && !entry.getKey().isRemoved()) return false;
            entry.getKey().end();
            return true;
        });
        WorldData data = WorldData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            WorldData.Trip trip = data.trips.get(player.getUUID());
            if (trip == null) continue;
            boolean inside = Hyperspace.inHyperspace(player);
            if (trip.island >= Zones.BASE && !Zones.isZone(trip.island)) {
                if (player.tickCount > 20) leave(player, inside);
                continue;
            }
            // past the event's first glitch there is no time limit, and none once the way home opens either
            boolean held = HoopaEvent.held(trip.island);
            if (!held || HoopaEvent.counting(trip.island)) trip.ticksLeft--;
            if (trip.ticksLeft <= 0) {
                leave(player, inside);
                if (inside && trip.splice) DistortionSplicer.collapse(player);
            } else if (inside && held) {
                Component clock = HoopaEvent.clock(trip.island, time(trip));
                if (clock != null) player.displayClientMessage(clock, true);
            } else if (inside && (trip.splice ? server.getTickCount() % 10 : trip.ticksLeft % 20) == 0) {
                player.displayClientMessage(timeLeft(trip, player.getRandom()), true);
                data.setDirty();
            }
        }
    }

    private static String time(WorldData.Trip trip) {
        int total = (trip.ticksLeft + 19) / 20;
        return "Time Remaining: " + total / 60 + "m " + String.format("%02d", total % 60) + "s";
    }

    private static Component timeLeft(WorldData.Trip trip, RandomSource random) {
        if (!trip.splice) return Component.literal(time(trip));
        MutableComponent unknown = Component.literal("???");
        if (random.nextInt(3) == 0) unknown.withStyle(ChatFormatting.OBFUSCATED);
        return Component.literal("Time Remaining: ").append(unknown);
    }

    public static boolean ready(MinecraftServer server) {
        return server.getLevel(Hyperspace.HYPERSPACE) != null;
    }

    public static boolean enter(ServerPlayer player, Portals.Entry portal) {
        if (WorldData.get(player.server).trips.containsKey(player.getUUID())) {
            if (Hyperspace.inHyperspace(player)) return false;
            leave(player, false);
        }
        return start(player, portal);
    }

    public static void exit(ServerPlayer player) {
        if (WorldData.get(player.server).trips.containsKey(player.getUUID())) leave(player, true);
    }

    public static boolean fresh(ServerPlayer player, boolean inside) {
        if (!fresh.contains(player.getUUID())) return false;
        if (!inside) fresh.remove(player.getUUID());
        return true;
    }

    private static boolean start(ServerPlayer player, Portals.Entry portal) {
        MinecraftServer server = player.server;
        WorldData data = WorldData.get(server);
        if (data.trips.containsKey(player.getUUID())) return false;

        WorldData.Trip trip = new WorldData.Trip();
        trip.dim = portal.level.dimension();
        trip.x = portal.base.x;
        trip.y = portal.base.y;
        trip.z = portal.base.z;
        trip.yaw = player.getYRot();
        trip.pitch = player.getXRot();
        int now = server.getTickCount();
        if (portal.endsAt < 0) portal.endsAt = now + portal.seconds * 20;
        trip.ticksLeft = Math.max(20, portal.endsAt - now);
        trip.legendaryChance = portal.legendaryChance;
        trip.alphaChance = portal.alphaChance;
        trip.powers = portal.powers;
        trip.poolSeed = portal.poolSeed;
        trip.legendaryShiny = portal.legendaryShiny;
        trip.legendaryAlpha = portal.legendaryAlpha;
        trip.types = new ArrayList<>(portal.types);
        trip.rarity = portal.rarity;
        trip.shiny = portal.shiny;
        trip.stars = portal.stars;
        trip.island = portal.island;
        trip.splice = portal.splice;
        trip.hoopa = portal.hoopa;
        trip.megaPending = !portal.hoopa && (portal.mega != null || player.getRandom().nextFloat() < DonutBoosts.megaChance(trip.stars));
        trip.megaSpecies = portal.mega == null || DonutBoosts.RANDOM.equals(portal.mega) ? null : portal.mega;
        trip.legendaryPending = portal.legendary != null;
        trip.legendarySpecies = DonutBoosts.RANDOM.equals(portal.legendary) ? null : portal.legendary;
        data.trips.put(player.getUUID(), trip);
        data.setDirty();

        Zones.Zone zone = Zones.get(portal.island);
        doors.put(player.getUUID(), portal.normal);
        Recall.sentOut(player);
        player.teleportTo(server.getLevel(Hyperspace.HYPERSPACE), zone.ax, zone.ay, zone.az, Set.of(), zone.yaw, 0f);
        fresh.add(player.getUUID());
        if (!trip.hoopa) LevelScaling.boost(player, trip.stars);
        Spawns.sweep(portal.island);
        Tasks.arrive(player, trip);
        if (trip.hoopa) HoopaEvent.enter(player, trip.island);
        if (trip.legendaryPending) HyperspaceAdvancements.legendary(player);
        if (trip.megaPending) HyperspaceAdvancements.mega(player);
        notifySound(player, SoundEvents.PORTAL_TRAVEL, 0.4f, 1.4f);
        return true;
    }

    public static void leave(ServerPlayer player, boolean sendBack) {
        MinecraftServer server = player.server;
        WorldData data = WorldData.get(server);
        WorldData.Trip trip = data.trips.remove(player.getUUID());
        Vec3 normal = doors.remove(player.getUUID());
        fresh.remove(player.getUUID());
        if (trip == null) return;
        data.setDirty();
        if (sendBack) {
            ServerLevel level = server.getLevel(trip.dim);
            if (level == null || trip.x == 0 && trip.y == 0 && trip.z == 0) {
                home(player);
                Tasks.left(player);
                ended(server, trip.island);
                return;
            }
            double yaw = Math.toRadians(trip.yaw);
            if (normal == null) normal = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
            Vec3 base = new Vec3(trip.x, trip.y, trip.z), at = base.add(normal.scale(STEP_IN));
            Recall.sentOut(player);
            player.teleportTo(level, at.x, at.y, at.z, Set.of(), (float) Math.toDegrees(Math.atan2(-normal.x, normal.z)), 0f);
            if (!Portals.openAt(level, base)) remnants.put(Rift.opened(level, base, normal, trip.splice), REMNANT);
            notifySound(player, SoundEvents.PORTAL_TRAVEL, 0.4f, 1.4f);
        }
        Tasks.left(player);
        ended(server, trip.island);
    }

    private static void home(ServerPlayer player) {
        MinecraftServer server = player.server;
        BlockPos spot = player.getRespawnPosition();
        ServerLevel level = spot == null ? null : server.getLevel(player.getRespawnDimension());
        if (level == null) {
            level = server.overworld();
            spot = level.getSharedSpawnPos();
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot);
        Recall.sentOut(player);
        player.teleportTo(level, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, Set.of(), player.getYRot(), 0f);
        notifySound(player, SoundEvents.PORTAL_TRAVEL, 0.4f, 1.4f);
    }

    static void notifySound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
    }

    static void ended(MinecraftServer server, int island) {
        Tasks.ended(server, island);
        if (!Zones.isZone(island) || Portals.opening(island)) return;
        if (WorldData.get(server).trips.values().stream().anyMatch(other -> other.island == island)) return;
        HoopaEvent.ended(island);
        Zones.release(server.getLevel(Hyperspace.HYPERSPACE), island);
    }
}
