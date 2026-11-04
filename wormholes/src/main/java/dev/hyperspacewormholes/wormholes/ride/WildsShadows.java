package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.world.Zone;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Ultra Space Wilds' shadow encounters, as in the game: the Pokemon past the wormhole waits out of reach at its world's
 * hiding place, drawn as a black shape (ShadowSilhouetteMixin, ShadowThroughWater). When a player comes within REVEAL_RANGE
 * of the zone's spot (or NOTICE_RANGE of the Pokemon) it cries, crouches, runs to its hiding place's mouth if it has one,
 * then jumps along an arc down to the spot (Cliff World's swoops down in a spiral, SWOOP_*) and lands as an ordinary wild
 * Pokemon. While hidden it can't be hurt, battled, caught or despawned and has no AI; that state lives on the entity (TAG
 * plus Cobblemon's saved Unbattleable and HideLabel flags), so it survives a reload. Each arrival's Pokemon carries ARRIVAL
 * and removes the last trip's from that world; every SWEEP_TICKS any older trip's Pokemon that isn't battling is removed.
 */
public final class WildsShadows {
    private static final String TAG = "hyperspace_wormholes.shadow";
    private static final String ARRIVAL = "hyperspace_wormholes.wilds";
    private static final double REVEAL_RANGE = 14;
    private static final double NOTICE_RANGE = 8;
    private static final int CROUCH_TICKS = 14;
    private static final double RUN_SPEED = 0.4;
    private static final double LEAP_SPEED = 0.9;
    private static final double CIRCLE_HEIGHT = 40;
    private static final double CIRCLE_RADIUS = 10;
    private static final double CIRCLE_SPEED = 0.35;
    private static final double SWOOP_SINK = 0.45;
    private static final double SWOOP_TURN = 2.5 * Math.PI;
    private static final double SWOOP_LEVEL = 0.85;
    private static final double SWOOP_FACE = 0.8;
    private static final float SWOOP_PITCH = 55, SWOOP_PITCH_STEP = 4;
    private static final int SWEEP_TICKS = 20;
    private static final Object BUSY = new Object();
    private static final Map<ResourceKey<Level>, Hide> HIDES = Map.of(
            Wormholes.WILDS_CLIFF, new Hide(null, null, 2),
            Wormholes.WILDS_CRAG, new Hide(new Vec3(4122.5, 76, -5.5), null, 4),
            Wormholes.WILDS_CAVE, new Hide(new Vec3(8218.5, 67, -6.5), new Vec3(8219.5, 67, 0.5), 4),
            Wormholes.WILDS_WATER, new Hide(new Vec3(12313.5, 76, -10.5), new Vec3(12313.5, 76, -7.5), 3));

    private record Hide(@Nullable Vec3 ledge, @Nullable Vec3 mouth, double arc) {}

    private static final class Shadow {
        final ServerLevel level;
        final PokemonEntity entity;
        final Vec3 spot;
        final Hide hide;
        int age;
        int leap = -1;
        Vec3 from = Vec3.ZERO;
        double fromAngle;
        @Nullable UUID trigger;

        Shadow(ServerLevel level, PokemonEntity entity, Vec3 spot, Hide hide) {
            this.level = level;
            this.entity = entity;
            this.spot = spot;
            this.hide = hide;
        }
    }

    private static final List<Shadow> HIDDEN = new ArrayList<>();
    private static final Map<ResourceKey<Level>, UUID> CURRENT = new HashMap<>();
    private static final List<PokemonEntity> STALE = new ArrayList<>();

    private WildsShadows() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register(WildsShadows::onLoad);
        ServerTickEvents.END_SERVER_TICK.register(WildsShadows::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            HIDDEN.clear();
            CURRENT.clear();
            STALE.clear();
        });
    }

    public static boolean isShadow(Entity entity) {
        return entity instanceof PokemonEntity pokemon && pokemon.isNoAi()
                && UltraZones.WILDS.contains(pokemon.level().dimension())
                && pokemon.getEntityData().get(PokemonEntity.getUNBATTLEABLE());
    }

    public static boolean isFlyingShadow(Entity entity) {
        return isShadow(entity) && entity.level().dimension() == Wormholes.WILDS_CLIFF;
    }

    static void hide(ServerLevel level, PokemonEntity entity, Vec3 arrival) {
        Zone zone = UltraZones.of(level);
        Hide hide = HIDES.get(level.dimension());
        if (zone == null || zone.spot() == null || hide == null) return;
        level.getEntities(EntityTypeTest.forClass(PokemonEntity.class), WildsShadows::replaceable).forEach(Entity::discard);
        entity.addTag(TAG);
        entity.addTag(ARRIVAL);
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        entity.getEntityData().set(PokemonEntity.getHIDE_LABEL(), true);
        entity.getEntityData().set(PokemonEntity.getUNBATTLEABLE(), true);
        Vec3 spot = zone.standOn(zone.spot());
        Vec3 at = hide.ledge() != null ? hide.ledge() : circling(spot, 0);
        entity.moveTo(at.x, at.y, at.z, facing(at, arrival), 0f);
        entity.setYHeadRot(entity.getYRot());
        entity.setYBodyRot(entity.getYRot());
        CURRENT.put(level.dimension(), entity.getUUID());
        level.addFreshEntity(entity);
    }

    private static boolean replaceable(PokemonEntity pokemon) {
        return pokemon.getTags().contains(ARRIVAL) && !pokemon.isBattling()
                && HIDDEN.stream().noneMatch(s -> s.entity == pokemon && s.leap >= 0);
    }

    private static void onLoad(Entity entity, ServerLevel level) {
        if (!(entity instanceof PokemonEntity pokemon) || !pokemon.getTags().contains(ARRIVAL)) return;
        Zone zone = UltraZones.of(level);
        Hide hide = HIDES.get(level.dimension());
        if (zone == null || zone.spot() == null || hide == null) {
            pokemon.removeTag(ARRIVAL);
            release(pokemon);
            return;
        }
        UUID current = CURRENT.putIfAbsent(level.dimension(), pokemon.getUUID());
        if (current != null && !current.equals(pokemon.getUUID())) {
            STALE.add(pokemon);
            return;
        }
        if (!pokemon.getTags().contains(TAG) || HIDDEN.stream().anyMatch(s -> s.entity == pokemon)) return;
        pokemon.getBusyLocks().add(BUSY);
        HIDDEN.add(new Shadow(level, pokemon, zone.standOn(zone.spot()), hide));
    }

    private static void tick(MinecraftServer server) {
        STALE.forEach(Entity::discard);
        STALE.clear();
        if (server.getTickCount() % SWEEP_TICKS == 0) sweep(server);
        for (Iterator<Shadow> it = HIDDEN.iterator(); it.hasNext(); ) {
            Shadow s = it.next();
            if (s.entity.isRemoved() || s.entity.level() != s.level) {
                it.remove();
                continue;
            }
            s.age++;
            Player near = s.level.getNearestPlayer(s.entity.getX(), s.entity.getY(), s.entity.getZ(), -1, false);
            if (s.leap < 0) {
                idle(s, near);
                if (near != null && (near.distanceToSqr(s.spot) < REVEAL_RANGE * REVEAL_RANGE
                        || near.distanceToSqr(s.entity) < NOTICE_RANGE * NOTICE_RANGE)) {
                    s.leap = 0;
                    s.trigger = near.getUUID();
                    WormholeSounds.cry(s.entity);
                }
                continue;
            }
            s.leap++;
            if (s.leap < CROUCH_TICKS) {
                idle(s, near);
                continue;
            }
            if (s.leap == CROUCH_TICKS) {
                s.from = s.entity.position();
                s.fromAngle = circleAngle(s);
            }
            if (move(s, s.leap - CROUCH_TICKS)) {
                land(s);
                it.remove();
            }
        }
    }

    private static void sweep(MinecraftServer server) {
        CURRENT.forEach((dimension, current) -> {
            ServerLevel level = server.getLevel(dimension);
            if (level == null) return;
            level.getEntities(EntityTypeTest.forClass(PokemonEntity.class), pokemon -> pokemon.getTags().contains(ARRIVAL)
                    && !pokemon.getUUID().equals(current) && !pokemon.isBattling()).forEach(Entity::discard);
        });
    }

    private static void idle(Shadow s, @Nullable Player near) {
        if (s.hide.ledge() == null) {
            double angle = circleAngle(s);
            place(s.entity, circling(s.spot, angle), (float) (angle * Mth.RAD_TO_DEG));
            return;
        }
        place(s.entity, s.hide.ledge(), near != null ? facing(s.hide.ledge(), near.position()) : s.entity.getYRot());
    }

    private static boolean move(Shadow s, int ticks) {
        if (s.hide.ledge() == null) return swoop(s, ticks);
        Vec3 mouth = s.hide.mouth() != null ? s.hide.mouth() : s.from;
        int run = (int) Math.ceil(s.from.distanceTo(mouth) / RUN_SPEED);
        if (ticks < run) {
            place(s.entity, s.from.lerp(mouth, ticks / (double) run), facing(s.from, mouth));
            return false;
        }
        if (ticks == run && wet(s.level, mouth)) splash(s.level, mouth);
        int leap = Math.max(1, (int) Math.ceil(mouth.distanceTo(s.spot) / LEAP_SPEED));
        double t = Math.min(1, (ticks - run) / (double) leap);
        place(s.entity, mouth.lerp(s.spot, t).add(0, s.hide.arc() * 4 * t * (1 - t), 0), facing(mouth, s.spot));
        return t >= 1;
    }

    private static boolean swoop(Shadow s, int ticks) {
        int length = (int) Math.ceil(CIRCLE_HEIGHT / SWOOP_SINK);
        double u = Math.min(1, ticks / (double) length);
        Vec3 at = spiral(s, u, length), before = spiral(s, Math.max(0, (ticks - 1) / (double) length), length);
        Vec3 velocity = at.subtract(before);
        float yaw = velocity.horizontalDistanceSqr() > 1e-4 ? facing(before, at) : s.entity.getYRot();
        Player look = s.trigger != null ? s.level.getPlayerByUUID(s.trigger) : null;
        if (look != null && u > SWOOP_FACE) {
            yaw = Mth.rotLerp((float) smooth((u - SWOOP_FACE) / (1 - SWOOP_FACE)), yaw, facing(at, look.position()));
        }
        float dive = (float) Mth.clamp(-Mth.atan2(velocity.y, velocity.horizontalDistance()) * Mth.RAD_TO_DEG, 0,
                SWOOP_PITCH);
        place(s.entity, at, yaw, Mth.approach(s.entity.getXRot(), u >= 1 ? 0 : dive, SWOOP_PITCH_STEP));
        return u >= 1;
    }

    private static Vec3 spiral(Shadow s, double u, int length) {
        double circled = CIRCLE_SPEED / CIRCLE_RADIUS * length;
        double angle = s.fromAngle + circled * u + (SWOOP_TURN - circled) * smooth(u);
        double radius = CIRCLE_RADIUS * (1 - smooth(u));
        double height = (s.from.y - s.spot.y) * (1 - smooth(Math.min(1, u / SWOOP_LEVEL)));
        return s.spot.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
    }

    private static double smooth(double u) {
        return u * u * (3 - 2 * u);
    }

    private static double circleAngle(Shadow s) {
        return s.age * CIRCLE_SPEED / CIRCLE_RADIUS;
    }

    private static Vec3 circling(Vec3 spot, double angle) {
        return spot.add(Math.cos(angle) * CIRCLE_RADIUS, CIRCLE_HEIGHT, Math.sin(angle) * CIRCLE_RADIUS);
    }

    static float facing(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(from.x - to.x, to.z - from.z) * Mth.RAD_TO_DEG);
    }

    static void place(PokemonEntity entity, Vec3 at, float yaw) {
        place(entity, at, yaw, 0);
    }

    private static void place(PokemonEntity entity, Vec3 at, float yaw, float pitch) {
        entity.setPos(at);
        entity.setXRot(pitch);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setYRot(yaw);
        entity.setYBodyRot(yaw);
        entity.setYHeadRot(yaw);
        entity.fallDistance = 0;
    }

    private static boolean wet(ServerLevel level, Vec3 at) {
        return level.getFluidState(BlockPos.containing(at)).is(FluidTags.WATER);
    }

    private static void splash(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 1.2f, 1f);
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.9, at.z, 40, 0.8, 0.2, 0.8, 0.2);
    }

    private static void land(Shadow s) {
        if (wet(s.level, s.spot)) {
            splash(s.level, s.spot);
        } else {
            s.level.playSound(null, s.spot.x, s.spot.y, s.spot.z, SoundEvents.GENERIC_BIG_FALL, SoundSource.NEUTRAL, 1.5f, 0.8f);
            s.level.sendParticles(ParticleTypes.POOF, s.spot.x, s.spot.y + 0.2, s.spot.z, 16, 0.6, 0.1, 0.6, 0.05);
        }
        ServerPlayer player = s.trigger != null && s.level.getPlayerByUUID(s.trigger) instanceof ServerPlayer p
                && p.isAlive() && !p.isSpectator() ? p : null;
        Player look = player != null ? player : s.level.getNearestPlayer(s.spot.x, s.spot.y, s.spot.z, -1, false);
        if (look != null) place(s.entity, s.spot, facing(s.spot, look.position()));
        release(s.entity);
        Pokemon pokemon = s.entity.getPokemon();
    }

    private static void release(PokemonEntity entity) {
        entity.removeTag(TAG);
        entity.setNoAi(false);
        entity.setNoGravity(false);
        entity.setInvulnerable(false);
        entity.getEntityData().set(PokemonEntity.getHIDE_LABEL(), false);
        entity.getEntityData().set(PokemonEntity.getUNBATTLEABLE(), false);
        entity.getBusyLocks().remove(BUSY);
    }
}
