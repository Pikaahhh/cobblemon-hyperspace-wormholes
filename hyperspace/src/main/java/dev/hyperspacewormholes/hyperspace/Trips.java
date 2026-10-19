package dev.hyperspacewormholes.hyperspace;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;

/**
 * Hyperspace trips: starting one through a portal, the time left on the action bar, and sending you back where you
 * came from when time's up, you die or you leave. Your sent-out Pokemon get recalled before every teleport so
 * Cobblemon can't crash the server.
 */
public class Trips {
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Trips::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) leave(player, false);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            if (origin.dimension().equals(Hyperspace.HYPERSPACE)) leave(player, false);
        });
    }

    private static void tick(MinecraftServer server) {
        WorldData data = WorldData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            WorldData.Trip trip = data.trips.get(player.getUUID());
            if (trip == null) continue;
            if (trip.island >= Zones.BASE && !Zones.isZone(trip.island)) {
                if (player.tickCount > 20) leave(player, true);
                continue;
            }
            trip.ticksLeft--;
            if (trip.ticksLeft <= 0) {
                leave(player, true);
            } else if (trip.ticksLeft % 20 == 0) {
                int total = (trip.ticksLeft + 19) / 20;
                String time = total / 60 + "m " + String.format("%02d", total % 60) + "s";
                player.displayClientMessage(Component.literal("Time Remaining: " + time), true);
                data.setDirty();
            }
        }
    }

    public static boolean ready(MinecraftServer server) {
        return server.getLevel(Hyperspace.HYPERSPACE) != null;
    }

    public static boolean start(ServerPlayer player, Portals.Entry portal) {
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
        trip.poolSeed = portal.poolSeed;
        trip.legendaryShiny = portal.legendaryShiny;
        trip.legendaryAlpha = portal.legendaryAlpha;
        trip.types = new ArrayList<>(portal.types);
        trip.rarity = portal.rarity;
        trip.shiny = portal.shiny;
        trip.stars = portal.stars;
        trip.island = portal.island;
        trip.megaPending = portal.mega != null || trip.stars == 4 && player.getRandom().nextInt(5) == 0;
        trip.megaSpecies = portal.mega == null || DonutBoosts.RANDOM.equals(portal.mega) ? null : portal.mega;
        trip.legendaryPending = portal.legendary != null;
        trip.legendarySpecies = DonutBoosts.RANDOM.equals(portal.legendary) ? null : portal.legendary;
        data.trips.put(player.getUUID(), trip);
        data.setDirty();

        Zones.Zone zone = Zones.get(portal.island);
        BlockPos spawn = BlockPos.containing(zone.ax, zone.ay, zone.az);
        Recall.sentOut(player);
        player.teleportTo(server.getLevel(Hyperspace.HYPERSPACE), spawn.getX() + 0.5, spawn.getY(),
                spawn.getZ() + 0.5, zone.yaw, 0f);
        LevelScaling.boost(player, trip.stars);
        Spawns.sweep(portal.island);
        Tasks.arrive(player, trip);
        player.playNotifySound(SoundEvents.PORTAL_TRAVEL, SoundSource.MASTER, 0.4f, 1.4f);
        return true;
    }

    public static void leave(ServerPlayer player, boolean sendBack) {
        WorldData data = WorldData.get(player.server);
        WorldData.Trip trip = data.trips.remove(player.getUUID());
        if (trip == null) return;
        data.setDirty();
        if (sendBack) {
            ServerLevel level = player.server.getLevel(trip.dim);
            if (level == null) level = player.server.overworld();
            Recall.sentOut(player);
            player.teleportTo(level, trip.x, trip.y, trip.z, trip.yaw, trip.pitch);
            player.playNotifySound(SoundEvents.PORTAL_TRAVEL, SoundSource.MASTER, 0.4f, 1.4f);
        }
        Tasks.left(player);
        ended(player.server, trip.island);
    }

    static void ended(MinecraftServer server, int island) {
        Tasks.ended(server, island);
        if (!Zones.isZone(island) || Portals.opening(island)) return;
        if (WorldData.get(server).trips.values().stream().anyMatch(other -> other.island == island)) return;
        Zones.release(server.getLevel(Hyperspace.HYPERSPACE), island);
    }
}
