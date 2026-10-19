package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.math.Transformation;
import dev.hyperspacewormholes.hyperspace.mixin.DisplayAccessor;
import dev.hyperspacewormholes.hyperspace.mixin.ItemDisplayAccessor;
import dev.hyperspacewormholes.hyperspace.mixin.TextDisplayAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hyperspace portals: opening one from a Hoopa Ring (if there's ground and room), the ring display growing in, walking
 * through to start a trip to a fresh island, and closing it.
 */
public class Portals {
    private static final int OPEN_TICKS = 300;
    private static final double HEIGHT = 2.5;
    private static final double WIDTH = 2.2;
    private static final double LIFT = 0.25;
    private static final String TAG = "hyperspace_wormholes_portal";
    private static final float RING_SCALE = 2.5f * 48 / 36;
    private static final int GROW_TICKS = 15;
    private static final int SHRINK_TICKS = 10;
    private static final int CLOSED_TICKS = 6;
    private static final float CLOSED_SCALE = 0.9f;

    private static final List<Entry> entries = new ArrayList<>();
    private static final Map<Integer, Return> returns = new HashMap<>();
    private static final Map<UUID, Integer> cooldowns = new HashMap<>();
    private static final Set<UUID> ours = new HashSet<>();
    private static final Map<Display, Integer> growing = new HashMap<>();
    private static final Map<Display, Integer> closing = new HashMap<>();
    private static int age;

    public static class Entry {
        ServerLevel level;
        Vec3 base, normal;
        int ticksLeft = OPEN_TICKS;
        int stars, rarity, shiny, seconds, island;
        List<String> types;
        String legendary, mega;
        float legendaryChance, alphaChance, legendaryShiny, legendaryAlpha;
        long poolSeed;
        int endsAt = -1;
        Display ring;
        Display.TextDisplay label;
        final Set<UUID> entered = new HashSet<>();
    }

    private record Return(Vec3 base, Vec3 normal, Display ring) {
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
        Vec3 base = player.position().add(facing(player).scale(2.5));
        AABB below = new AABB(base.x - 0.3, base.y - 1.0, base.z - 0.3, base.x + 0.3, base.y - 1.0E-3, base.z + 0.3);
        return !player.serverLevel().noCollision(below);
    }

    public static boolean opening(int island) {
        for (Entry portal : entries) if (portal.island == island) return true;
        return false;
    }

    public static boolean fits(ServerPlayer player) {
        Vec3 normal = facing(player);
        Vec3 base = player.position().add(normal.scale(2.5)).add(0, LIFT, 0);
        Vec3 side = new Vec3(-normal.z, 0, normal.x).scale(WIDTH / 2);
        AABB box = new AABB(base.add(side).add(normal.scale(0.2)), base.subtract(side).subtract(normal.scale(0.2)).add(0, HEIGHT, 0));
        return player.serverLevel().noCollision(box);
    }

    public static boolean open(ServerPlayer player, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        Entry portal = new Entry();
        portal.level = level;
        portal.normal = facing(player);
        portal.base = player.position().add(portal.normal.scale(2.5));
        portal.stars = DonutBoosts.stars(stack);
        portal.rarity = DonutBoosts.rarity(stack);
        portal.shiny = DonutBoosts.shinyRolls(stack);
        portal.seconds = DonutBoosts.seconds(stack);
        portal.legendary = DonutBoosts.legendary(stack);
        portal.mega = DonutBoosts.mega(stack);
        portal.legendaryChance = DonutBoosts.legendaryChance(stack);
        if (portal.legendary == null && player.getRandom().nextFloat() < portal.legendaryChance) portal.legendary = DonutBoosts.RANDOM;
        portal.alphaChance = DonutBoosts.alphaChance(stack);
        portal.poolSeed = player.getRandom().nextLong();
        portal.legendaryShiny = DonutBoosts.legendaryShiny(stack);
        portal.legendaryAlpha = DonutBoosts.legendaryAlpha(stack);
        portal.types = DonutBoosts.types(stack);
        if (portal.types.isEmpty()) portal.types = List.of(DonutBoosts.TYPES.get(player.getRandom().nextInt(DonutBoosts.TYPES.size())));
        if (DonutBoosts.RANDOM.equals(portal.legendary)) {
            Species legend = Spawns.pickLegendary(portal.types, player.getRandom());
            if (legend != null) portal.legendary = legend.getResourceIdentifier().getPath();
        }
        Set<Integer> busy = new HashSet<>();
        for (WorldData.Trip trip : WorldData.get(player.server).trips.values()) busy.add(trip.island);
        for (Entry open : entries) busy.add(open.island);
        ServerLevel hyperspace = player.server.getLevel(Hyperspace.HYPERSPACE);
        portal.island = Zones.build(player, hyperspace, level, portal.base, portal.normal, busy);
        if (portal.island < 0) return false;
        portal.ring = ring(level, portal.base, portal.normal);

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
        grow();
        shrink();
        cooldowns.replaceAll((id, ticks) -> ticks - 1);
        cooldowns.values().removeIf(ticks -> ticks <= 0);

        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry portal = it.next();
            portal.ticksLeft--;
            sparkle(portal.level, portal.base);
            if (age % 80 == 0) hum(portal.level, portal.base);
            if (portal.ticksLeft % 20 == 0) setLabel(portal);

            for (ServerPlayer player : new ArrayList<>(portal.level.players())) {
                if (player.isSpectator() || portal.entered.contains(player.getUUID()) || cooldowns.containsKey(player.getUUID())) continue;
                if (inside(portal.base, portal.normal, player) && Trips.start(player, portal)) {
                    portal.entered.add(player.getUUID());
                    cooldowns.put(player.getUUID(), 40);
                }
            }

            if (portal.ticksLeft <= 0) {
                close(portal.ring);
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
            close(entry.getValue().ring);
            return true;
        });
        for (int island : occupied) {
            returns.computeIfAbsent(island, i -> {
                Zones.Zone zone = Zones.get(i);
                Vec3 base = new Vec3(zone.rx, zone.ry, zone.rz), normal = new Vec3(zone.nx, 0, zone.nz);
                return new Return(base, normal, ring(hyperspace, base, normal));
            });
        }

        for (Return portal : returns.values()) {
            sparkle(hyperspace, portal.base);
            if (age % 80 == 40) hum(hyperspace, portal.base);
        }
        for (ServerPlayer player : new ArrayList<>(hyperspace.players())) {
            if (cooldowns.containsKey(player.getUUID())) continue;
            WorldData.Trip trip = data.trips.get(player.getUUID());
            if (trip == null) continue;
            Return portal = returns.get(trip.island);
            if (portal != null && inside(portal.base, portal.normal, player)) {
                cooldowns.put(player.getUUID(), 40);
                Trips.leave(player, true);
            }
        }
    }

    private static Display ring(ServerLevel level, Vec3 base, Vec3 normal) {
        Display.ItemDisplay ring = EntityType.ITEM_DISPLAY.create(level);
        ring.setPos(base.x, base.y + LIFT + HEIGHT / 2, base.z);
        ring.setYRot((float) Math.toDegrees(Math.atan2(-normal.x, normal.z)));
        ((ItemDisplayAccessor) ring).hyperspace_wormholes$setItem(new ItemStack(ModItems.HOOPA_PORTAL));
        ((DisplayAccessor) ring).hyperspace_wormholes$setTransformation(size(0.05f));
        ((DisplayAccessor) ring).hyperspace_wormholes$setBrightness(new Brightness(15, 15));
        add(level, ring);
        growing.put(ring, 2);
        return ring;
    }

    private static void grow() {
        growing.entrySet().removeIf(entry -> {
            Display ring = entry.getKey();
            if (ring.isRemoved()) return true;
            if (entry.getValue() > 1) {
                entry.setValue(entry.getValue() - 1);
                return false;
            }
            ((DisplayAccessor) ring).hyperspace_wormholes$setDuration(GROW_TICKS);
            ((DisplayAccessor) ring).hyperspace_wormholes$setDelay(1);
            ((DisplayAccessor) ring).hyperspace_wormholes$setTransformation(size(RING_SCALE));
            return true;
        });
    }

    private static void close(Display ring) {
        growing.remove(ring);
        ring = live(ring);
        if (ring.isRemoved()) {
            remove(ring);
            return;
        }
        ((DisplayAccessor) ring).hyperspace_wormholes$setDuration(SHRINK_TICKS);
        ((DisplayAccessor) ring).hyperspace_wormholes$setDelay(0);
        ((DisplayAccessor) ring).hyperspace_wormholes$setTransformation(size(CLOSED_SCALE));
        closing.put(ring, 0);
    }

    private static void shrink() {
        closing.entrySet().removeIf(entry -> {
            Display ring = entry.getKey();
            int age = entry.getValue() + 1;
            entry.setValue(age);
            if (ring.isRemoved()) {
                remove(ring);
                return true;
            }
            if (age == SHRINK_TICKS) ((ItemDisplayAccessor) ring).hyperspace_wormholes$setItem(new ItemStack(ModItems.HOOPA_PORTAL_CLOSED));
            if (age < SHRINK_TICKS + CLOSED_TICKS) return false;
            ServerLevel level = (ServerLevel) ring.level();
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.HOOPA_PORTAL_CLOSED)),
                    ring.getX(), ring.getY(), ring.getZ(), 40, 0.35, 0.35, 0.35, 0.12);
            level.playSound(null, ring.getX(), ring.getY(), ring.getZ(), SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1f, 1f);
            remove(ring);
            return true;
        });
    }

    private static Transformation size(float scale) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, 1f), new Quaternionf());
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

    private static void sparkle(ServerLevel level, Vec3 base) {
        RandomSource random = level.getRandom();
        if (random.nextInt(3) != 0) return;
        double y = base.y + LIFT + random.nextDouble() * HEIGHT;
        level.sendParticles(ParticleTypes.PORTAL, base.x + (random.nextDouble() - 0.5) * WIDTH, y,
                base.z + (random.nextDouble() - 0.5) * WIDTH, 2, 0, 0, 0, 0.3);
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
