package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.pokemon.Species;
import dev.hyperspacewormholes.hyperspace.mixin.DisplayAccessor;
import dev.hyperspacewormholes.hyperspace.mixin.TextDisplayAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hyperspace portals: opening one at a spatial distortion (or in front of you from /hyperspace portal) if there's ground and room, drawn as Z-A's gate opening (Rift.OPENING ticks, and you can only go in once it's open), walking through to start a trip to a fresh island, and closing it.
 * The way back inside Hyperspace is a Rift portal too, flat against a wall on the island (placed by IslandWall); it opens as you arrive and closes once nobody's left. Walking into either hands you to Trips (Trips.enter, Trips.exit), and you can't walk back into the exit before you've stepped out of it once (Trips.fresh).
 * A Distortion Splicer opens a splice: the same portal with its timer hidden and an unstable look, no donut powers, and maybe the Hoopa Unbound event (hoopa: no legendary or Mega on it); whoever opened it is let in first, and while HoopaEvent.locked holds for an island its way back sits small and does nothing.
 */
public class Portals {
    private static final int OPEN_TICKS = 300;
    private static final double HEIGHT = 2.5;
    private static final double WIDTH = 2.2;
    private static final double LIFT = 0.25;
    private static final String TAG = "hyperspace_wormholes_portal";

    private static final List<Entry> entries = new ArrayList<>();
    private static final Map<Integer, Return> returns = new HashMap<>();
    private static final Map<UUID, Integer> cooldowns = new HashMap<>();
    private static final Set<UUID> ours = new HashSet<>();
    private static int age;

    public static class Entry {
        ServerLevel level;
        Vec3 base, normal;
        int ticksLeft = OPEN_TICKS;
        int stars, rarity, shiny, seconds, island;
        List<String> types;
        String legendary, mega;
        float legendaryChance, alphaChance, legendaryShiny, legendaryAlpha;
        DonutPowers powers = DonutPowers.NONE;
        long poolSeed;
        int endsAt = -1;
        boolean splice, hoopa;
        UUID opener;
        Rift rift;
        Display.TextDisplay label;
        final Set<UUID> entered = new HashSet<>();
    }

    private record Return(Vec3 base, Vec3 normal, Rift rift) {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Portals::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.getTags().contains(TAG) && !ours.contains(entity.getUUID())) entity.discard();
        });
    }

    private static Vec3 facing(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 normal = new Vec3(look.x, 0, look.z);
        return normal.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, player.getYRot()) : normal.normalize();
    }

    public static boolean grounded(ServerPlayer player) {
        return grounded(player.level(), player.position().add(facing(player).scale(2.5)));
    }

    public static boolean grounded(Level level, Vec3 base) {
        AABB below = new AABB(base.x - 0.3, base.y - 1.0, base.z - 0.3, base.x + 0.3, base.y - 1.0E-3, base.z + 0.3);
        return !level.noCollision(below);
    }

    public static boolean openAt(ServerLevel level, Vec3 base) {
        for (Entry portal : entries) {
            if (portal.level == level && portal.base.distanceToSqr(base) < 1 && !portal.rift.isRemoved() && portal.rift.ends() == 0) return true;
        }
        return false;
    }

    public static boolean opening(int island) {
        for (Entry portal : entries) if (portal.island == island) return true;
        return false;
    }

    public static boolean fits(ServerPlayer player) {
        Vec3 normal = facing(player);
        return fits(player.level(), player.position().add(normal.scale(2.5)), normal);
    }

    public static boolean fits(Level level, Vec3 portalBase, Vec3 normal) {
        Vec3 base = portalBase.add(0, LIFT, 0);
        Vec3 side = new Vec3(-normal.z, 0, normal.x).scale(WIDTH / 2);
        AABB box = new AABB(base.add(side).add(normal.scale(0.2)), base.subtract(side).subtract(normal.scale(0.2)).add(0, HEIGHT, 0));
        return level.noCollision(box);
    }

    public static boolean open(ServerPlayer player, ItemStack stack) {
        Vec3 normal = facing(player);
        return open(player, stack, player.position().add(normal.scale(2.5)), normal, DonutBoosts.stars(stack),
                DonutBoosts.types(stack), DonutBoosts.seconds(stack), null, null);
    }

    public static boolean open(ServerPlayer player, ItemStack stack, Vec3 base, Vec3 normal, int stars, List<String> types, int seconds,
                               String legendary, String mega) {
        return open(player, stack, base, normal, stars, types, seconds, legendary, mega, false, false);
    }

    public static boolean splice(ServerPlayer player, Vec3 base, Vec3 normal, int stars, List<String> types, int seconds, boolean hoopa,
                                 String legendary, String mega) {
        return open(player, ItemStack.EMPTY, base, normal, stars, types, seconds, legendary, mega, true, hoopa);
    }

    private static boolean open(ServerPlayer player, ItemStack stack, Vec3 base, Vec3 normal, int stars, List<String> types, int seconds,
                                String legendary, String mega, boolean splice, boolean hoopa) {
        ServerLevel level = player.serverLevel();
        Entry portal = new Entry();
        portal.level = level;
        portal.normal = normal;
        portal.base = base;
        portal.stars = stars;
        portal.rarity = DonutBoosts.rarity(stack);
        portal.shiny = DonutBoosts.shinyRolls(stack);
        portal.seconds = seconds;
        portal.splice = splice;
        portal.hoopa = hoopa;
        portal.opener = player.getUUID();
        portal.legendary = hoopa ? null : legendary != null ? legendary : DonutBoosts.legendary(stack);
        portal.mega = hoopa ? null : mega != null ? mega : DonutBoosts.mega(stack);
        portal.legendaryChance = hoopa ? 0 : DonutBoosts.legendaryChance(stack, stars);
        if (portal.legendary == null && player.getRandom().nextFloat() < portal.legendaryChance) portal.legendary = DonutBoosts.RANDOM;
        portal.alphaChance = DonutBoosts.alphaChance(stack);
        portal.powers = DonutPowers.of(DonutBoosts.ids(stack));
        portal.poolSeed = player.getRandom().nextLong();
        portal.legendaryShiny = DonutBoosts.legendaryShiny(stack);
        portal.legendaryAlpha = DonutBoosts.legendaryAlpha(stack);
        portal.types = new ArrayList<>(types);
        if (portal.types.isEmpty()) portal.types = List.of(DonutBoosts.TYPES.get(player.getRandom().nextInt(DonutBoosts.TYPES.size())));
        if (DonutBoosts.RANDOM.equals(portal.legendary)) {
            Species legend = Spawns.pickLegendary(portal.types, player.getRandom());
            if (legend != null) portal.legendary = legend.getResourceIdentifier().getPath();
        }
        Set<Integer> busy = new HashSet<>();
        for (WorldData.Trip trip : WorldData.get(player.server).trips.values()) busy.add(trip.island);
        for (Entry open : entries) busy.add(open.island);
        ServerLevel hyperspace = player.server.getLevel(Hyperspace.HYPERSPACE);
        portal.island = Zones.build(player, hyperspace, level, portal.base, portal.normal, busy, hoopa);
        if (portal.island < 0) return false;
        portal.rift = Rift.portal(level, portal.base, portal.normal, splice);

        portal.label = EntityType.TEXT_DISPLAY.create(level);
        portal.label.setPos(portal.base.x, portal.base.y + LIFT + HEIGHT + 0.35, portal.base.z);
        ((DisplayAccessor) portal.label).hyperspace_wormholes$setBillboard(Display.BillboardConstraints.CENTER);
        setLabel(portal);
        add(level, portal.label);
        entries.add(portal);
        return true;
    }

    private static void tick(MinecraftServer server) {
        age++;
        cooldowns.replaceAll((id, ticks) -> ticks - 1);
        cooldowns.values().removeIf(ticks -> ticks <= 0);

        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry portal = it.next();
            portal.ticksLeft--;
            if (age % 80 == 0) hum(portal.level, portal.base);
            if (portal.ticksLeft % 20 == 0) setLabel(portal);

            List<ServerPlayer> near = new ArrayList<>(portal.level.players());
            near.sort((a, b) -> Boolean.compare(!a.getUUID().equals(portal.opener), !b.getUUID().equals(portal.opener)));
            for (ServerPlayer player : near) {
                if (player.isSpectator() || portal.entered.contains(player.getUUID()) || cooldowns.containsKey(player.getUUID())) continue;
                if (portal.rift.open() && inside(portal.base, portal.normal, player) && Trips.enter(player, portal)) {
                    portal.entered.add(player.getUUID());
                    cooldowns.put(player.getUUID(), 40);
                }
            }

            if (portal.ticksLeft <= 0) {
                portal.rift.end();
                remove(portal.label);
                it.remove();
                Trips.ended(server, portal.island);
            }
        }

        ServerLevel hyperspace = server.getLevel(Hyperspace.HYPERSPACE);
        if (hyperspace == null) return;
        WorldData data = WorldData.get(server);

        Set<Integer> occupied = new HashSet<>();
        for (ServerPlayer player : hyperspace.players()) {
            WorldData.Trip trip = data.trips.get(player.getUUID());
            if (trip != null && Zones.isZone(trip.island)) occupied.add(trip.island);
        }
        returns.entrySet().removeIf(entry -> {
            if (occupied.contains(entry.getKey())) return false;
            entry.getValue().rift().end();
            return true;
        });
        for (int island : occupied) {
            returns.computeIfAbsent(island, i -> {
                Zones.Zone zone = Zones.get(i);
                Vec3 base = new Vec3(zone.rx, zone.ry, zone.rz), normal = new Vec3(zone.nx, 0, zone.nz);
                // the way home from the Hoopa event isn't shaky, for all that a splice opened it
                boolean splice = !HoopaEvent.held(i) && data.trips.values().stream().anyMatch(trip -> trip.island == i && trip.splice);
                return new Return(base, normal, Rift.portal(hyperspace, base, normal, splice));
            }).rift().lock(HoopaEvent.locked(island));
        }

        for (Return portal : returns.values()) {
            if (age % 80 == 40) hum(hyperspace, portal.base);
        }
        for (ServerPlayer player : new ArrayList<>(hyperspace.players())) {
            if (cooldowns.containsKey(player.getUUID())) continue;
            WorldData.Trip trip = data.trips.get(player.getUUID());
            if (trip == null) continue;
            Return portal = returns.get(trip.island);
            boolean in = portal != null && !HoopaEvent.locked(trip.island) && inside(portal.base, portal.normal, player);
            if (!Trips.fresh(player, in) && in) {
                cooldowns.put(player.getUUID(), 40);
                Trips.exit(player);
            }
        }
    }

    private static void add(ServerLevel level, Entity entity) {
        entity.addTag(TAG);
        ours.add(entity.getUUID());
        level.addFreshEntity(entity);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> T live(T entity) {
        if (!entity.isRemoved() || !(entity.level() instanceof ServerLevel level)) return entity;
        Entity found = level.getEntity(entity.getUUID());
        return found != null && found.getClass() == entity.getClass() ? (T) found : entity;
    }

    private static void remove(Entity entity) {
        ours.remove(entity.getUUID());
        entity.discard();
        live(entity).discard();
    }

    private static void hum(ServerLevel level, Vec3 base) {
        float pitch = 0.8f + level.getRandom().nextFloat() * 0.4f;
        level.playSound(null, base.x, base.y + LIFT + HEIGHT / 2, base.z, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.5f, pitch);
    }

    private static void setLabel(Entry portal) {
        int seconds = (portal.ticksLeft + 19) / 20;
        ((TextDisplayAccessor) portal.label).hyperspace_wormholes$setText(
                Component.literal("Closes in " + seconds + "s").withStyle(ChatFormatting.GOLD));
    }

    private static boolean inside(Vec3 base, Vec3 normal, ServerPlayer player) {
        Vec3 d = player.position().subtract(base);
        double along = d.x * normal.x + d.z * normal.z;
        double side = -d.x * normal.z + d.z * normal.x;
        return Math.abs(along) < 0.5 && Math.abs(side) < WIDTH / 2 && d.y > -0.6 && d.y < HEIGHT - 0.5;
    }
}
