package dev.hyperspacewormholes.wormholes.ride;

import dev.hyperspacewormholes.wormholes.entity.WormholeEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The ways home left where wormholes come out. Walk into one and you go back to where you started. Each one closes
 * after a while or when nobody's around.
 */
public final class WaysHome {
    private static final int FLASH_TICKS = 15;
    private static final double CLOSE_RANGE = 128;
    private static final int CHECK_INTERVAL = 20;

    private record Pending(HomePoint home, int ticksLeft) {}

    private record Open(ServerLevel level, UUID entity, long closesAt, UUID owner, boolean untilEmpty, ChunkPos chunk) {}

    private static final TicketType<ChunkPos> KEEP_OPEN = TicketType.create("hyperspace_wormholes_way_home", Comparator.comparingLong(ChunkPos::toLong));

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final List<Open> OPEN = new ArrayList<>();

    private WaysHome() {}

    public static void track(WormholeEntity wormhole, int lifetimeTicks, UUID owner) {
        ServerLevel level = (ServerLevel) wormhole.level();
        OPEN.add(new Open(level, wormhole.getUUID(), level.getGameTime() + lifetimeTicks, owner, false, wormhole.chunkPosition()));
    }

    public static void trackUntilEmpty(WormholeEntity wormhole, UUID owner) {
        ServerLevel level = (ServerLevel) wormhole.level();
        ChunkPos chunk = wormhole.chunkPosition();
        level.getChunkSource().addRegionTicket(KEEP_OPEN, chunk, 2, chunk);
        OPEN.add(new Open(level, wormhole.getUUID(), Long.MAX_VALUE, owner, true, chunk));
    }

    public static boolean hasOpenUntilEmpty(ServerLevel level) {
        return OPEN.stream().anyMatch(open -> open.untilEmpty() && open.level() == level);
    }

    private static void forget(Open open) {
        if (open.untilEmpty()) open.level().getChunkSource().removeRegionTicket(KEEP_OPEN, open.chunk(), 2, open.chunk());
    }

    private static void close(ServerLevel level, WormholeEntity wormhole) {
        Vec3 c = wormhole.center();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 1.5f, 1.4f);
        wormhole.discard();
    }

    public static void closeFor(ServerPlayer player) {
        for (Iterator<Open> it = OPEN.iterator(); it.hasNext(); ) {
            Open open = it.next();
            if (!open.owner().equals(player.getUUID())) continue;
            it.remove();
            forget(open);
            Entity entity = open.level().getEntity(open.entity());
            if (entity instanceof WormholeEntity wormhole && !wormhole.isRemoved()) close(open.level(), wormhole);
        }
    }

    private static void checkOpen(MinecraftServer server) {
        if (OPEN.isEmpty() || server.getTickCount() % CHECK_INTERVAL != 0) return;
        for (Iterator<Open> it = OPEN.iterator(); it.hasNext(); ) {
            Open open = it.next();
            Entity entity = open.level().getEntity(open.entity());
            if (entity == null || entity.isRemoved()) {
                it.remove();
                forget(open);
                continue;
            }
            boolean expired = open.level().getGameTime() >= open.closesAt();
            boolean abandoned = open.untilEmpty() ? open.level().players().isEmpty()
                    : !open.level().hasNearbyAlivePlayer(entity.getX(), entity.getY(), entity.getZ(), CLOSE_RANGE);
            if (expired || abandoned) {
                close(open.level(), (WormholeEntity) entity);
                it.remove();
                forget(open);
            }
        }
    }

    public static void send(ServerPlayer player, HomePoint home) {
        if (PENDING.containsKey(player.getUUID()) || UltraWarpRide.isRiding(player)) return;
        PENDING.put(player.getUUID(), new Pending(home, FLASH_TICKS));
        UltraWarpRide.flash(player, FLASH_TICKS);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 2f, 1.6f);
    }

    public static void tick(MinecraftServer server) {
        checkOpen(server);
        if (PENDING.isEmpty()) return;
        Map<ServerPlayer, HomePoint> due = new LinkedHashMap<>();
        for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Pending> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Pending pending = e.getValue();
            boolean gone = player == null || !player.isAlive();
            if (!gone && pending.ticksLeft() > 1) {
                e.setValue(new Pending(pending.home(), pending.ticksLeft() - 1));
                continue;
            }
            it.remove();
            if (!gone) due.put(player, pending.home());
        }
        due.forEach(UltraWarpRide::sendHome);
    }

    public static void onDisconnect(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }
}
