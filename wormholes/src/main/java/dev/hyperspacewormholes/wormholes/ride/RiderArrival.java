package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.wormholes.network.RiderEmergePayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Coming out of the wormhole at the end of a ride: holds you on your landing spot while clients show you gliding out. */
public final class RiderArrival {
    private static final int EMERGE_TICKS = 80;
    private static final int SETTLE_TICKS = 15;
    static final int TICKS = EMERGE_TICKS + SETTLE_TICKS;
    public static final float EXIT_PITCH = 8f;
    private static final int TIMEOUT_TICKS = TICKS + 20 * 20;
    private static final double VIEW_RANGE = 128;
    private static final int GUARD_TICKS = 5 * 20;

    private static final class Rider {
        final ServerLevel level;
        final ServerPlayer player;
        final List<ServerPlayer> passengers;
        @Nullable PokemonEntity mount;
        final @Nullable Pokemon pokemon;
        final Vec3 landing;
        final float yaw;
        final boolean airborne;
        int age;

        Rider(ServerPlayer player, List<ServerPlayer> passengers, @Nullable PokemonEntity mount, Vec3 landing, boolean airborne) {
            this.level = player.serverLevel();
            this.player = player;
            this.passengers = List.copyOf(passengers);
            this.mount = mount;
            this.pokemon = mount != null ? mount.getPokemon() : null;
            this.landing = landing;
            this.yaw = player.getYRot();
            this.airborne = airborne;
        }
    }

    private record Guard(ServerPlayer player, PokemonEntity mount, int until) {}

    private static final Map<UUID, Rider> ACTIVE = new HashMap<>();
    private static final List<Guard> GUARDS = new ArrayList<>();
    private static int ticks;

    private RiderArrival() {}

    static void start(ServerPlayer player, List<ServerPlayer> passengers, @Nullable PokemonEntity mount, Vec3 mouth,
                      Vec3 landing, boolean airborne) {
        Rider previous = ACTIVE.remove(player.getUUID());
        if (previous != null) release(previous, false);
        Rider rider = new Rider(player, passengers, mount, landing, airborne);
        ACTIVE.put(player.getUUID(), rider);
        UltraWarpRules.hold(player);
        rider.passengers.forEach(UltraWarpRules::hold);
        if (mount != null) UltraWarpRules.hold(mount);

        RiderEmergePayload payload = new RiderEmergePayload(player.getId(), rider.passengers.stream().map(ServerPlayer::getId).toList(),
                mouth, landing, EMERGE_TICKS, SETTLE_TICKS, airborne);
        List<ServerPlayer> viewers = new ArrayList<>(PlayerLookup.around(rider.level, landing, VIEW_RANGE));
        if (!viewers.contains(player)) viewers.add(player);
        for (ServerPlayer p : rider.passengers) if (!viewers.contains(p)) viewers.add(p);
        for (ServerPlayer viewer : viewers) ServerPlayNetworking.send(viewer, payload);
    }

    public static boolean isEmerging(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID()) || ACTIVE.values().stream().anyMatch(r -> r.passengers.contains(player));
    }

    public static void onDone(ServerPlayer player) {
        Rider rider = ACTIVE.remove(player.getUUID());
        if (rider != null) release(rider, true);
    }

    public static void tick(MinecraftServer server) {
        ticks++;
        guard();
        if (ACTIVE.isEmpty()) return;
        List<Rider> done = new ArrayList<>();
        for (Rider rider : ACTIVE.values()) {
            rider.age++;
            if (rider.pokemon != null && rider.age % 10 == 0 && !rider.player.isRemoved()
                    && (rider.mount == null || rider.mount.isRemoved() || rider.player.getVehicle() != rider.mount)) {
                if (rider.mount != null) UltraWarpRules.release(rider.mount);
                if (rider.player.getVehicle() != null) Mounts.dismount(rider.player);
                rider.mount = Mounts.mount(rider.player, rider.pokemon, rider.level, rider.landing, rider.yaw);
                if (rider.mount != null) UltraWarpRules.hold(rider.mount);
            }
            if (rider.mount != null && !rider.mount.isRemoved() && rider.age % 10 == 0) {
                Mounts.seatPassengers(rider.passengers.stream().filter(p -> !p.isRemoved() && p.level() == rider.level).toList(),
                        rider.mount);
            }
            if (rider.mount != null && !rider.mount.isRemoved() && rider.player.getVehicle() == rider.mount
                    && rider.age % 10 == 5) {
                remindSeated(rider);
            }
            if (rider.player.isRemoved() || rider.player.level() != rider.level || rider.age >= TIMEOUT_TICKS) done.add(rider);
        }
        for (Rider rider : done) {
            ACTIVE.remove(rider.player.getUUID());
            release(rider, true);
        }
    }

    private static void release(Rider rider, boolean animate) {
        UltraWarpRules.release(rider.player);
        rider.passengers.forEach(UltraWarpRules::release);
        if (rider.mount != null) UltraWarpRules.release(rider.mount);
        if (rider.airborne) {
            if (rider.mount != null && !rider.mount.isRemoved() && rider.player.getVehicle() == rider.mount) remindSeated(rider);
            if (rider.mount != null && !rider.mount.isRemoved()) GUARDS.add(new Guard(rider.player, rider.mount, ticks + GUARD_TICKS));
            return;
        }
        if (rider.mount != null && rider.player.getVehicle() == rider.mount) Mounts.dismount(rider.player);
        PokemonEntity mount = rider.mount;
        if (mount == null || mount.isRemoved()) return;
        Pokemon pokemon = mount.getPokemon();
        Vec3 at = mount.position();
        if (animate && mount.level() == rider.level && !rider.player.isRemoved()) {
            rider.level.playSound(null, mount.getX(), mount.getY(), mount.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 1.2f);
            mount.recallWithAnimation().whenComplete((recalled, error) -> {
                if (!mount.isRemoved()) mount.discard();
                Mounts.removeCopies(rider.level, pokemon, at);
            });
        } else {
            Mounts.putAway(pokemon, rider.player);
            if (!mount.isRemoved()) mount.discard();
            Mounts.removeCopies(rider.level, pokemon, at);
        }
    }

    private static void remindSeated(Rider rider) {
        ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(rider.mount);
        rider.level.getChunkSource().broadcast(rider.mount, packet);
        rider.player.connection.send(packet);
        for (ServerPlayer p : rider.passengers) p.connection.send(packet);
    }

    public static void onDisconnect(ServerPlayer player) {
        Rider rider = ACTIVE.remove(player.getUUID());
        if (rider != null) release(rider, false);
    }

    private static void guard() {
        if (GUARDS.isEmpty()) return;
        GUARDS.removeIf(g -> ticks > g.until() || g.player().isRemoved() || !g.player().isAlive() || g.mount().isRemoved()
                || g.player().level() != g.mount().level() || g.player().isShiftKeyDown());
        for (Guard g : GUARDS) {
            if (g.player().getVehicle() != g.mount() && ticks % 5 == 0) Mounts.seat(g.player(), g.mount());
        }
    }

    public static void finishAll() {
        GUARDS.clear();
        for (Rider rider : ACTIVE.values()) release(rider, false);
        ACTIVE.clear();
    }
}
