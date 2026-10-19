package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.wormholes.CosmicJar;
import dev.hyperspacewormholes.wormholes.Flutes;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.WormholeConfig;
import dev.hyperspacewormholes.wormholes.compat.WaystoneLinks;
import dev.hyperspacewormholes.wormholes.entity.WormholeEntity;
import dev.hyperspacewormholes.wormholes.network.RideEndPayload;
import dev.hyperspacewormholes.wormholes.network.RideFollowPayload;
import dev.hyperspacewormholes.wormholes.network.RideMusicPayload;
import dev.hyperspacewormholes.wormholes.network.RideStartPayload;
import dev.hyperspacewormholes.wormholes.network.RideStatePayload;
import dev.hyperspacewormholes.wormholes.network.WarpFlashPayload;
import dev.hyperspacewormholes.wormholes.ride.WormholeDestinations.Arrival;
import dev.hyperspacewormholes.wormholes.world.SwoleTreeFeature;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of Ultra Warp Rides. Starts and ends rides, holds the rider and mount in the void dimension, checks what
 * the client says, works out which wormholes and rings you went through, and handles cooldowns and passengers. Flying
 * into a wormhole whites out the screen and you come out at its destination with a wild Pokemon waiting for you.
 */
public final class UltraWarpRide {
    private static final double ANCHOR_SPACING = 2048;
    private static final double ANCHOR_Y = 100;
    private static final int STRAY_GRACE_TICKS = 20;
    private static final double STRAY_RADIUS = 64;
    private static final int REENTRY_COOLDOWN_TICKS = 100;
    private static final int RUN_COOLDOWN_TICKS = 150 * 20;
    private static final int UNCHARTED_EXIT_TICKS = 60;
    private static final int FLUTE_EXIT_TICKS = 100;
    private static final int FLASH_TICKS = 15;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Integer> STRAY_TICKS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    private static final Map<UUID, UUID> PASSENGER_OF = new HashMap<>();
    private static final BitSet USED_SLOTS = new BitSet();

    private UltraWarpRide() {}

    private static final class Session {
        final long seed;
        final int slot;
        final Vec3 anchor;
        final TunnelLayout layout;
        final List<WaystoneLinks.Target> waystones;
        final boolean summoned;
        final List<UUID> passengers = new ArrayList<>();
        final Stamina stamina = new Stamina();
        @Nullable Pokemon mount;
        int ticks;
        double progress;
        int nextWormhole;
        int nextGoldRing;
        double warpDistance;
        int dragTarget = -1;
        int returnAt = -1;
        WarpTunnel.@Nullable Wormhole through;
        WormholeDestinations.@Nullable Plan destination;
        WaystoneLinks.@Nullable Target waystoneDestination;
        StructureWormholes.@Nullable Target structureDestination;
        @Nullable FlatLandingSearch search;

        Session(long seed, int slot, Vec3 anchor, @Nullable Pokemon mount, TunnelLayout layout,
                List<WaystoneLinks.Target> waystones, boolean summoned) {
            this.seed = seed;
            this.slot = slot;
            this.anchor = anchor;
            this.mount = mount;
            this.layout = layout;
            this.waystones = waystones;
            this.summoned = summoned;
        }
    }

    public static boolean isRiding(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID()) || PASSENGER_OF.containsKey(player.getUUID());
    }

    private static boolean isDriving(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        return vehicle == null || vehicle.getControllingPassenger() == player;
    }

    private static boolean ridesAllowedMount(ServerPlayer player) {
        Pokemon ridden = Mounts.pokemonOf(player.getVehicle());
        return ridden != null && Mounts.isAllowed(ridden);
    }

    private static List<ServerPlayer> passengersOf(ServerPlayer player) {
        List<ServerPlayer> passengers = new ArrayList<>();
        Session s = SESSIONS.get(player.getUUID());
        if (s != null) {
            for (UUID id : s.passengers) {
                ServerPlayer p = player.server.getPlayerList().getPlayer(id);
                if (p != null && p.isAlive()) passengers.add(p);
            }
            return passengers;
        }
        Entity vehicle = player.getVehicle();
        if (vehicle == null || vehicle.getControllingPassenger() != player) return passengers;
        for (Entity e : vehicle.getPassengers()) {
            if (e instanceof ServerPlayer p && p != player && p.isAlive()) passengers.add(p);
        }
        return passengers;
    }

    public static boolean onCooldown(ServerPlayer player) {
        Long until = COOLDOWN_UNTIL.get(player.getUUID());
        return until != null && player.serverLevel().getGameTime() < until;
    }

    public static long runCooldownLeft(ServerPlayer player) {
        Long next = player.getAttached(Wormholes.NEXT_RUN);
        if (next == null) return 0;
        long left = next - player.serverLevel().getGameTime();
        return left <= 0 ? 0 : Math.min(left, RUN_COOLDOWN_TICKS);
    }

    public static void clearRunCooldown(ServerPlayer player) {
        player.removeAttached(Wormholes.NEXT_RUN);
    }

    public static boolean tellRunCooldown(ServerPlayer player) {
        long left = runCooldownLeft(player);
        if (left <= 0) return false;
        long seconds = (left + 19) / 20;
        String time = seconds >= 60 ? String.format("%dm %02ds", seconds / 60, seconds % 60) : seconds + "s";
        player.displayClientMessage(Component.translatable("hyperspace_wormholes.msg.run_cooldown", time)
                .withStyle(ChatFormatting.RED), true);
        return true;
    }

    public static boolean canEnter(ServerPlayer player) {
        if (isRiding(player) || onCooldown(player) || UltraWarpRules.inWarp(player) || UltraWarpRules.inHyperspace(player)
                || !isDriving(player) || tellRunCooldown(player)) {
            return false;
        }
        return !WormholeConfig.get().requireMount || ridesAllowedMount(player);
    }

    public static boolean canUseWayHome(ServerPlayer player) {
        if (RiderArrival.isEmerging(player) || !isDriving(player)) return false;
        return !WormholeConfig.get().requireMount || UltraBeastWorlds.isHome(player.level()) || ridesAllowedMount(player);
    }

    public static void start(ServerPlayer player, Vec3 returnPos, float returnYaw) {
        start(player, returnPos, returnYaw, null, null);
    }

    public static void start(ServerPlayer player, Vec3 returnPos, float returnYaw, @Nullable String override) {
        start(player, returnPos, returnYaw, override, null);
    }

    public static void startSummoned(ServerPlayer player, Pokemon legend) {
        start(player, player.position(), player.getYRot(), null, legend);
    }

    private static void start(ServerPlayer player, Vec3 returnPos, float returnYaw, @Nullable String override,
                              @Nullable Pokemon summoned) {
        ServerLevel warp = player.server.getLevel(Wormholes.ULTRA_WARP);
        if (warp == null) {
            Wormholes.LOGGER.error("Ultra Warp dimension is missing; is the mod's datapack loaded?");
            return;
        }

        Entity vehicle = player.getVehicle();
        Pokemon ridden = Mounts.pokemonOf(vehicle);
        Pokemon carried = summoned != null ? summoned : Mounts.findMount(player);
        WaysHome.closeFor(player);
        RideLegend kind = Mounts.kindOf(carried);
        List<ServerPlayer> passengers = ridden != null && ridden == carried ? passengersOf(player) : List.of();

        HomePoint start = regularReturnPoint(player, new HomePoint(player.level().dimension(), returnPos, returnYaw));
        ServerPlayNetworking.send(player, RideMusicPayload.INSTANCE);
        player.setAttached(Wormholes.RETURN_POINT, start);
        for (ServerPlayer p : passengers) {
            WaysHome.closeFor(p);
            ServerPlayNetworking.send(p, RideMusicPayload.INSTANCE);
            p.setAttached(Wormholes.RETURN_POINT, start);
            Mounts.dismount(p);
        }

        if (vehicle != null) Mounts.dismount(player);
        if (ridden != null) Mounts.putAway(ridden, player);
        if (carried != null && carried != ridden) Mounts.putAway(carried, player);

        int slot = USED_SLOTS.nextClearBit(0);
        USED_SLOTS.set(slot);
        Vec3 anchor = new Vec3(slot * ANCHOR_SPACING + 8.5, ANCHOR_Y, 8.5);
        warp.getChunk(BlockPos.containing(anchor));
        place(player, warp, anchor, 0f);
        passengers.forEach(p -> place(p, warp, anchor, 0f));
        PokemonEntity mountEntity = carried != null ? Mounts.mount(player, carried, warp, anchor, 0f) : null;
        if (mountEntity != null) Mounts.seatPassengers(passengers, mountEntity);

        long seed = warp.getRandom().nextLong();
        List<WaystoneLinks.Target> waystones = carried != null && WaystoneLinks.isWarpStone(carried.heldItem())
                ? WaystoneLinks.activated(player) : List.of();
        List<RideStartPayload.WaystoneHole> holes = new ArrayList<>();
        TunnelLayout layout;
        if (waystones.isEmpty()) {
            layout = TunnelLayout.seeded(seed, override);
        } else {
            waystones = WaystoneWormholes.sortByDistance(waystones, returnPos);
            List<WarpTunnel.Wormhole> wormholes = WaystoneWormholes.layout(seed, waystones, returnPos);
            List<String> names = waystones.stream().map(WaystoneLinks.Target::name).toList();
            layout = TunnelLayout.fixed(seed, wormholes, names);
            for (int i = 0; i < wormholes.size(); i++) {
                WarpTunnel.Wormhole w = wormholes.get(i);
                holes.add(new RideStartPayload.WaystoneHole(w.distance(), w.x(), w.y(), w.type().ordinal(), names.get(i)));
            }
        }

        Session session = new Session(seed, slot, anchor, carried, layout, waystones, summoned != null);
        SESSIONS.put(player.getUUID(), session);
        long nextRun = warp.getGameTime() + RUN_COOLDOWN_TICKS;
        String overrideName = layout.override() == null ? "" : layout.override();
        player.setAttached(Wormholes.NEXT_RUN, nextRun);
        Flutes.showCooldown(player);
        STRAY_TICKS.remove(player.getUUID());
        ServerPlayNetworking.send(player, new RideStartPayload(seed, kind.ordinal(), holes, overrideName, false));
        CosmicJar.maybeCharge(player);
        for (ServerPlayer p : passengers) {
            p.setAttached(Wormholes.NEXT_RUN, nextRun);
            Flutes.showCooldown(p);
            CosmicJar.maybeCharge(p);
            session.passengers.add(p.getUUID());
            PASSENGER_OF.put(p.getUUID(), player.getUUID());
            STRAY_TICKS.remove(p.getUUID());
            ServerPlayNetworking.send(p, new RideStartPayload(seed, kind.ordinal(), holes, overrideName, true));
        }
    }

    private static boolean isUltra(ResourceKey<Level> dimension) {
        return dimension == Wormholes.ULTRA_WARP || UltraBeastWorlds.HOMES.containsValue(dimension);
    }

    private static HomePoint regularReturnPoint(ServerPlayer player, HomePoint point) {
        if (!isUltra(point.dimension())) {
            player.setAttached(Wormholes.ULTRA_HOME, point);
            return point;
        }
        HomePoint last = player.getAttached(Wormholes.ULTRA_HOME);
        return last != null && !isUltra(last.dimension()) ? last : HomePoint.worldSpawn(player.server);
    }

    private static void place(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw) {
        player.teleportTo(level, pos.x, pos.y, pos.z, yaw, 0f);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    private static void landed(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw) {
        place(player, level, pos, yaw);
        COOLDOWN_UNTIL.put(player.getUUID(), level.getGameTime() + REENTRY_COOLDOWN_TICKS);
        ServerPlayNetworking.send(player, RideEndPayload.INSTANCE);
    }

    private static void landPassengers(List<ServerPlayer> passengers, ServerLevel level, Vec3 pos, float yaw) {
        float rad = yaw * Mth.DEG_TO_RAD;
        Vec3 side = new Vec3(Mth.cos(rad), 0, Mth.sin(rad));
        for (int i = 0; i < passengers.size(); i++) {
            ServerPlayer p = passengers.get(i);
            Vec3 at = pos.add(side.scale((i % 2 == 0 ? 1.5 : -1.5) * (1 + i / 2)));
            if (!level.noBlockCollision(p, p.getDimensions(p.getPose()).makeBoundingBox(at))) at = pos;
            p.removeAttached(Wormholes.RETURN_POINT);
            landed(p, level, at, yaw);
            CosmicJar.maybeCharge(p);
        }
    }

    static void flash(ServerPlayer player, int ticks) {
        WarpFlashPayload payload = new WarpFlashPayload(ticks);
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer p : passengersOf(player)) ServerPlayNetworking.send(p, payload);
    }

    private static void tell(ServerPlayer player, List<ServerPlayer> passengers, Component message) {
        player.sendSystemMessage(message);
        for (ServerPlayer p : passengers) p.sendSystemMessage(message);
    }

    public static void end(ServerPlayer player) {
        HomePoint point = player.getAttached(Wormholes.RETURN_POINT);
        player.removeAttached(Wormholes.RETURN_POINT);
        sendHome(player, point);
    }

    static void sendHome(ServerPlayer player, @Nullable HomePoint point) {
        if (point != null) point = regularReturnPoint(player, point);
        List<ServerPlayer> passengers = passengersOf(player);
        Pokemon carried = leaveRide(player);
        ServerLevel dest = point == null ? null : player.server.getLevel(point.dimension());
        if (dest == null) {
            point = HomePoint.worldSpawn(player.server);
            dest = player.server.overworld();
        }

        dest.getChunk(BlockPos.containing(point.pos()));
        Vec3 pos = WormholeDestinations.groundBelow(dest, point.pos());
        place(player, dest, pos, point.yRot());
        landPassengers(passengers, dest, pos, point.yRot());
        CosmicJar.maybeCharge(player);
        PokemonEntity entity = carried != null ? Mounts.mount(player, carried, dest, pos, point.yRot()) : null;
        if (entity != null) Mounts.seatPassengers(passengers, entity);

        COOLDOWN_UNTIL.put(player.getUUID(), dest.getGameTime() + REENTRY_COOLDOWN_TICKS);
        ServerPlayNetworking.send(player, RideEndPayload.INSTANCE);
    }

    private static @Nullable Pokemon leaveRide(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            USED_SLOTS.clear(session.slot);
            for (UUID id : session.passengers) {
                PASSENGER_OF.remove(id);
                ServerPlayer p = player.server.getPlayerList().getPlayer(id);
                if (p != null && p.getVehicle() != null) Mounts.dismount(p);
            }
        }
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            for (Entity e : List.copyOf(vehicle.getPassengers())) {
                if (e instanceof ServerPlayer p && p != player) Mounts.dismount(p);
            }
        }
        Pokemon carried = session != null && session.mount != null ? session.mount : Mounts.pokemonOf(vehicle);
        if (vehicle != null) Mounts.dismount(player);
        if (carried != null) Mounts.putAway(carried, player);
        return session != null && session.summoned ? null : carried;
    }

    private static long distance(Vec3 a, Vec3 b) {
        return Math.round(Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.z - b.z, 2)));
    }

    private static void arriveThroughWormhole(ServerPlayer player, Session s) {
        HomePoint home = takeReturnPoint(player);
        Arrival arrival = LandingClearing.clear(landingFor(player, s.destination, s.search));
        boolean uncharted = s.layout.isUncharted(s.through.index());
        WormholeType type = s.through.type();
        int exitTicks = (uncharted ? UNCHARTED_EXIT_TICKS : FLUTE_EXIT_TICKS) + WormholeEntity.CLOSE_TICKS;
        WormholeEntity exit = uncharted || s.summoned ? WormholeDestinations.openBehind(arrival, type, exitTicks, true)
                : WormholeDestinations.openReturnWormhole(arrival, home, type, WormholeDestinations.RETURN_WORMHOLE_TICKS, player);
        List<ServerPlayer> riders = land(player, arrival, exit);
        Vec3 pos = arrival.pos();
        if (uncharted) {
            tell(player, riders, Component.translatable("hyperspace_wormholes.msg.arrived_uncharted",
                    String.format("%,d", distance(pos, home.pos()))).withStyle(ChatFormatting.WHITE));
            return;
        }
        exit.setRainbow(s.through.rainbow());
        WormholeDestinations.spawnWild(arrival, s.through.rainbow());
        ServerLevel dest = arrival.level();
        long away = UltraBeastWorlds.isHome(dest) ? Math.round(Math.max(Math.abs(pos.x), Math.abs(pos.z))) : distance(pos, home.pos());
        tell(player, riders, Component.translatable("hyperspace_wormholes.msg.arrived", String.format("%,d", away),
                dimensionName(dest)).withStyle(ChatFormatting.WHITE));
    }

    private static void arriveAtWaystone(ServerPlayer player, WaystoneLinks.Target target) {
        HomePoint home = takeReturnPoint(player);
        Arrival arrival = WaystoneLinks.landing(player.server, target)
                .map(l -> WormholeDestinations.arrivalAt(l.level(), l.pos(), l.yaw()))
                .orElseGet(() -> {
                    ServerLevel level = player.server.getLevel(target.dimension());
                    if (level == null) level = player.server.overworld();
                    Vec3 near = WormholeDestinations.groundBelow(level, Vec3.atBottomCenterOf(target.pos().above()));
                    return WormholeDestinations.arrivalAt(level, near, player.getYRot());
                });
        WormholeEntity exit = WormholeDestinations.openReturnWormhole(arrival, home, WormholeType.BLUE,
                WormholeDestinations.WAYSTONE_RETURN_WORMHOLE_TICKS, player);
        List<ServerPlayer> riders = land(player, arrival, exit);
        tell(player, riders, Component.translatable("hyperspace_wormholes.msg.arrived_waystone", target.name())
                .withStyle(ChatFormatting.WHITE));
    }

    private static void arriveNearStructure(ServerPlayer player, Session s) {
        StructureWormholes.Target target = s.structureDestination;
        HomePoint home = takeReturnPoint(player);
        ServerLevel level = player.server.getLevel(target.dimension());
        if (level == null) level = player.server.overworld();
        StructureWormholes.SkySpot spot = StructureWormholes.skyNear(level, target.pos(), level.getRandom());

        List<ServerPlayer> riders = passengersOf(player);
        Pokemon carried = leaveRide(player);
        recallAll(player);
        Vec3 pos = spot.pos();
        boolean flying = carried != null && Mounts.canFly(carried);
        if (!flying) pos = WormholeDestinations.groundBelow(level, pos);
        landed(player, level, pos, spot.yaw());
        landPassengers(riders, level, pos, spot.yaw());
        CosmicJar.maybeCharge(player);

        WormholeEntity exit = s.summoned ? null : StructureWormholes.openWayHome(level, spot, home, player);
        if (carried != null) {
            PokemonEntity ridden = Mounts.mount(player, carried, level, pos, spot.yaw());
            if (ridden != null) Mounts.seatPassengers(riders, ridden);
            if (exit != null && flying) RiderArrival.start(player, riders, ridden, exit.center(), pos, true);
        }
        tell(player, riders, Component.translatable("hyperspace_wormholes.msg.arrived_structure",
                StructureWormholes.displayName(target.structure()), String.format("%,d", distance(pos, home.pos())),
                dimensionName(level)).withStyle(ChatFormatting.WHITE));
    }

    public static Component dimensionName(ServerLevel level) {
        return Component.translatableWithFallback("hyperspace_wormholes.dimension." + level.dimension().location().getPath(),
                StructureWormholes.displayName(level.dimension().location()));
    }

    private static HomePoint takeReturnPoint(ServerPlayer player) {
        HomePoint home = player.getAttached(Wormholes.RETURN_POINT);
        player.removeAttached(Wormholes.RETURN_POINT);
        return home != null ? home : HomePoint.worldSpawn(player.server);
    }

    private static List<ServerPlayer> land(ServerPlayer player, Arrival arrival, WormholeEntity exit) {
        List<ServerPlayer> passengers = passengersOf(player);
        leaveRide(player);
        recallAll(player);
        Vec3 pos = WormholeDestinations.floatStart(arrival, exit);
        landed(player, arrival.level(), pos, exit.getYRot());
        landPassengers(passengers, arrival.level(), pos, exit.getYRot());
        CosmicJar.maybeCharge(player);
        double drop = pos.y - arrival.pos().y;
        if (drop > 0.5) {
            floatDown(player, drop);
            passengers.forEach(p -> floatDown(p, drop));
        }
        return passengers;
    }

    private static void floatDown(ServerPlayer player, double drop) {
        int ticks = 40 + (int) Math.ceil(drop * 8);
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks, 0, false, false));
        player.fallDistance = 0;
    }

    private static void recallAll(ServerPlayer player) {
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon != null) Mounts.putAway(pokemon, player);
        }
    }

    private static void removeStrays(MinecraftServer server, ServerLevel warp) {
        Set<Entity> keep = new HashSet<>();
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            ServerPlayer rider = server.getPlayerList().getPlayer(entry.getKey());
            if (rider == null || rider.level() != warp) continue;
            if (rider.getVehicle() instanceof PokemonEntity ridden && !ridden.isRemoved()) {
                keep.add(ridden);
                continue;
            }
            Pokemon mount = entry.getValue().mount;
            if (mount == null) continue;
            warp.getEntitiesOfClass(PokemonEntity.class, rider.getBoundingBox().inflate(STRAY_RADIUS),
                            e -> e.getPokemon().getUuid().equals(mount.getUuid())).stream()
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(rider)))
                    .ifPresent(keep::add);
        }
        List<PokemonEntity> strays = new ArrayList<>();
        for (Entity e : warp.getAllEntities()) {
            if (e instanceof PokemonEntity p && !keep.contains(p)) strays.add(p);
        }
        strays.forEach(Entity::discard);
    }

    public static boolean blocksDismount(ServerPlayer player) {
        return !Mounts.allowDismount && player.isAlive() && (isRiding(player) || RiderArrival.isEmerging(player));
    }

    private static void statBoost(ServerPlayer player) {
        if (player.getVehicle() == null) return;
        SpawnSnowstormEntityParticlePacket packet = new SpawnSnowstormEntityParticlePacket(Wormholes.id("statup_quiet"),
                player.getVehicle().getId(), List.of("middle", "root"), null, List.of());
        packet.sendToPlayer(player);
        for (ServerPlayer p : passengersOf(player)) packet.sendToPlayer(p);
    }

    public static void onState(ServerPlayer player, RideStatePayload state) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;

        double progress = state.progress();
        if (!Double.isFinite(progress)) return;
        progress = Mth.clamp(progress, s.progress, (s.ticks + 40) * (double) WarpTunnel.MAX_SPEED * s.stamina.speedFactor());
        s.warpDistance = Math.min(WarpTunnel.MAX_WARP_DISTANCE, s.warpDistance + s.layout.warpDistance(progress - s.progress));

        float offX = Float.isFinite(state.offX()) ? state.offX() : 0f;
        float offY = Float.isFinite(state.offY()) ? state.offY() : 0f;
        float len = Mth.sqrt(offX * offX + offY * offY);
        if (len > WarpTunnel.MAX_OFFSET) {
            offX *= WarpTunnel.MAX_OFFSET / len;
            offY *= WarpTunnel.MAX_OFFSET / len;
        }

        for (; WarpTunnel.goldRingPassedAt(s.seed, s.nextGoldRing) <= progress; s.nextGoldRing++) {
            WarpTunnel.GoldRing ring = WarpTunnel.goldRing(s.seed, s.nextGoldRing);
            if (ring != null && WarpTunnel.touches(ring, offX, offY)) {
                s.stamina.collectRing();
                statBoost(player);
            }
        }

        for (; s.returnAt < 0; s.nextWormhole++) {
            WarpTunnel.Wormhole w = s.layout.wormhole(s.nextWormhole);
            if (w.distance() > progress) break;
            boolean dragged = s.dragTarget >= 0 && w.index() >= s.dragTarget;
            if (dragged || s.layout.isUncharted(w.index()) || WarpTunnel.touches(w, offX, offY)) travelWithFlash(player, s, w);
        }

        s.progress = progress;
        if (!s.passengers.isEmpty()) {
            RideFollowPayload follow = new RideFollowPayload(progress, offX, offY);
            for (ServerPlayer p : passengersOf(player)) ServerPlayNetworking.send(p, follow);
        }
    }

    private static void travelWithFlash(ServerPlayer player, Session s, WarpTunnel.Wormhole wormhole) {
        if (s.returnAt >= 0) return;
        boolean uncharted = s.layout.isUncharted(wormhole.index());
        if (s.layout.isFixed() && !uncharted && wormhole.index() >= s.waystones.size()) return;
        s.returnAt = s.ticks + FLASH_TICKS;
        flash(player, FLASH_TICKS);
        s.through = wormhole;
        if (uncharted) {
            s.destination = WormholeDestinations.planUncharted(player.server, player.getAttached(Wormholes.RETURN_POINT),
                    s.warpDistance, s.seed);
        } else if (s.layout.isFixed()) {
            s.waystoneDestination = s.waystones.get(wormhole.index());
        } else {
            s.destination = WormholeDestinations.plan(player.server, wormhole, s.seed, s.warpDistance, s.layout.override(),
                    player.getAttached(Wormholes.RETURN_POINT));
            if (wormhole.type() == WormholeType.PURPLE) {
                s.structureDestination = StructureWormholes.find(player.server, wormhole.rings(),
                        s.destination.x(), s.destination.z(), s.seed ^ (wormhole.index() * 0x632BE59BD9B4E019L));
                if (s.structureDestination != null) s.destination = null;
            }
        }
        if (s.destination != null) s.search = FlatLandingSearch.start(player.server, s.destination);
    }

    private static Arrival landingFor(ServerPlayer player, WormholeDestinations.Plan plan, @Nullable FlatLandingSearch search) {
        ServerLevel jungle = plan.dimension() == Wormholes.ULTRA_JUNGLE ? player.server.getLevel(plan.dimension()) : null;
        BlockPos platform = jungle == null ? null : SwoleTreeFeature.platform(jungle, plan.x(), plan.z());
        if (platform != null) return WormholeDestinations.arrival(jungle, platform, plan);
        if (search != null && search.result() != null) {
            return WormholeDestinations.arrival(player.server.getLevel(plan.dimension()), search.result(), plan);
        }
        return WormholeDestinations.resolve(player.server, plan);
    }

    public static void tick(MinecraftServer server) {
        List<ServerPlayer> returning = new ArrayList<>();
        Map<ServerPlayer, Session> arriving = new HashMap<>();
        for (Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Session> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Session s = entry.getValue();
            if (player == null || !UltraWarpRules.inWarp(player)) {
                it.remove();
                USED_SLOTS.clear(s.slot);
                s.passengers.forEach(PASSENGER_OF::remove);
                if (player != null) ServerPlayNetworking.send(player, RideEndPayload.INSTANCE);
                continue;
            }
            s.passengers.removeIf(id -> {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                boolean gone = p == null || !p.isAlive() || !UltraWarpRules.inWarp(p);
                if (gone) PASSENGER_OF.remove(id);
                return gone;
            });
            s.ticks++;
            s.stamina.tick(s.ticks);
            if (s.stamina.exhausted() && s.dragTarget < 0 && s.returnAt < 0) {
                s.dragTarget = s.layout.firstWormholeAfter(s.progress);
                if (Double.isInfinite(s.layout.wormhole(s.dragTarget).distance())) {
                    s.returnAt = s.ticks + FLASH_TICKS;
                    flash(player, FLASH_TICKS);
                }
            }
            keepMounted(player, s);
            boolean landingReady = s.search == null || s.search.step();
            if (s.returnAt >= 0 && s.ticks >= s.returnAt && landingReady) {
                if (s.destination != null || s.waystoneDestination != null || s.structureDestination != null) arriving.put(player, s);
                else returning.add(player);
            }
        }
        returning.forEach(UltraWarpRide::end);
        arriving.forEach((player, s) -> {
            if (s.structureDestination != null) arriveNearStructure(player, s);
            else if (s.waystoneDestination != null) arriveAtWaystone(player, s.waystoneDestination);
            else arriveThroughWormhole(player, s);
        });

        ServerLevel warp = server.getLevel(Wormholes.ULTRA_WARP);
        if (warp == null) return;
        if (server.getTickCount() % 10 == 0) removeStrays(server, warp);
        for (ServerPlayer player : warp.players().toArray(ServerPlayer[]::new)) {
            if (isRiding(player)) continue;
            if (STRAY_TICKS.merge(player.getUUID(), 1, Integer::sum) >= STRAY_GRACE_TICKS) {
                STRAY_TICKS.remove(player.getUUID());
                end(player);
            }
        }
    }

    private static void keepMounted(ServerPlayer player, Session s) {
        if (s.mount == null) return;
        PokemonEntity entity = player.getVehicle() instanceof PokemonEntity ridden && !ridden.isRemoved()
                && ridden.getPokemon().getUuid().equals(s.mount.getUuid()) ? ridden : s.mount.getEntity();
        if (entity == null && s.summoned && player.getVehicle() instanceof PokemonEntity other && !other.isRemoved()) {
            s.mount = other.getPokemon();
            entity = other;
        }
        boolean seated = entity != null && !entity.isRemoved() && entity.level() == player.level()
                && player.getVehicle() == entity && entity.hasPassenger(player);
        if (seated) {
            List<ServerPlayer> passengers = passengersOf(player);
            if (s.ticks % 10 == 0) Mounts.seatPassengers(passengers, entity);
            if (s.ticks % 20 == 0) {
                ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(entity);
                player.connection.send(packet);
                for (ServerPlayer p : passengers) p.connection.send(packet);
            }
            return;
        }
        if (s.ticks % 10 != 0) return;

        ServerLevel warp = player.serverLevel();
        if (entity == null || entity.isRemoved() || entity.level() != warp) {
            if (player.getVehicle() != null) Mounts.dismount(player);
            Mounts.putAway(s.mount, player);
            Mounts.mount(player, s.mount, warp, s.anchor, 0f);
        } else {
            entity.setPos(s.anchor);
            entity.setDeltaMovement(Vec3.ZERO);
            Mounts.seat(player, entity);
        }
    }

    public static void onDisconnect(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null) {
            USED_SLOTS.clear(s.slot);
            s.passengers.forEach(PASSENGER_OF::remove);
        }
        UUID driver = PASSENGER_OF.remove(player.getUUID());
        Session driving = driver != null ? SESSIONS.get(driver) : null;
        if (driving != null) driving.passengers.remove(player.getUUID());
        STRAY_TICKS.remove(player.getUUID());
        COOLDOWN_UNTIL.remove(player.getUUID());
    }
}
