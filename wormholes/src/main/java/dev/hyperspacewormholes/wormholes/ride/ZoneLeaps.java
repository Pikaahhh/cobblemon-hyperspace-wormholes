package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * An Ultra Beast waiting down in the world that leaps up to its zone's spot. In WORLDS it stands OUT blocks past the
 * spot, on the far side from the arrival, with no AI; when a player comes within REVEAL the ground rumbles, then it
 * cries, crouches, jumps along an arc and lands BACK blocks short of the spot (centred between the blocks either side
 * within ARMS_SEARCH), facing that player.
 * The group species + GROUP's JUMP, AIR and LAND animations are sent as primary animations at full strength from the
 * first frame (q.curve('one')), since Cobblemon's default fade-in hides the landing's impact. TAG keeps it waiting
 * after a reload.
 */
public final class ZoneLeaps {
    private static final String TAG = "hyperspace_wormholes.leap";
    private static final Set<ResourceKey<Level>> WORLDS = Set.of(Wormholes.ULTRA_JUNGLE);
    private static final double OUT = 24;
    private static final double REVEAL = 7;
    private static final String GROUP = "_leap", JUMP = "jump", AIR = "air", LAND = "land";
    private static final int RUMBLE_TICKS = 60;
    private static final int CROUCH_TICKS = 14;
    private static final double LEAP_SPEED = 1.4;
    private static final int AIR_EVERY = 24;
    private static final int JUMP_LEAD = 13;
    private static final int ARMS_SEARCH = 12;
    private static final double BACK = 2;
    private static final int TAKE_OFF = RUMBLE_TICKS + CROUCH_TICKS;

    private static final class Leap {
        final ServerLevel level;
        final PokemonEntity entity;
        Vec3 spot;
        Vec3 from;
        int leap = -1;

        Leap(ServerLevel level, PokemonEntity entity, Vec3 spot) {
            this.level = level;
            this.entity = entity;
            this.spot = spot;
            this.from = entity.position();
        }
    }

    private static final List<Leap> WAITING = new ArrayList<>();

    private ZoneLeaps() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register(ZoneLeaps::onLoad);
        ServerTickEvents.END_SERVER_TICK.register(ZoneLeaps::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WAITING.clear());
    }

    static boolean waits(ServerLevel level) {
        return WORLDS.contains(level.dimension());
    }

    static void hide(ServerLevel level, PokemonEntity entity, Zone zone, Vec3 arrival) {
        Vec3 spot = zone.standOn(zone.spot());
        Vec3 away = new Vec3(spot.x - arrival.x, 0, spot.z - arrival.z);
        double base = away.lengthSqr() > 1e-6 ? Mth.atan2(away.z, away.x) : 0;
        int x = Mth.floor(spot.x), z = Mth.floor(spot.z);
        for (int step = 0; step < 12; step++) {
            double angle = base + Math.toRadians(30 * ((step + 1) / 2) * (step % 2 == 0 ? 1 : -1));
            x = Mth.floor(spot.x + Math.cos(angle) * OUT);
            z = Mth.floor(spot.z + Math.sin(angle) * OUT);
            if (!zone.holdsAtOrBelow(x, level.getMaxBuildHeight(), z)) break;
        }
        level.getChunk(x >> 4, z >> 4);
        int ground = Math.max(level.getSeaLevel(), level.getChunkSource().getGenerator().getBaseHeight(x, z,
                Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState()));
        entity.moveTo(x + 0.5, ground, z + 0.5, WildsShadows.facing(new Vec3(x, ground, z), spot), 0f);
        entity.setYHeadRot(entity.getYRot());
        entity.setYBodyRot(entity.getYRot());
        entity.setNoAi(true);
        entity.addTag(TAG);
        level.addFreshEntity(entity);
        WAITING.add(new Leap(level, entity, spot));
    }

    private static void onLoad(Entity entity, ServerLevel level) {
        if (!(entity instanceof PokemonEntity pokemon) || !pokemon.getTags().contains(TAG)
                || WAITING.stream().anyMatch(l -> l.entity == pokemon)) return;
        Zone zone = UltraZones.of(level);
        if (zone == null || !waits(level)) {
            pokemon.removeTag(TAG);
            return;
        }
        zone = zone.near(pokemon.getX(), pokemon.getZ());
        if (zone.spot() != null) WAITING.add(new Leap(level, pokemon, zone.standOn(zone.spot())));
    }

    private static void tick(MinecraftServer server) {
        for (Iterator<Leap> it = WAITING.iterator(); it.hasNext(); ) {
            Leap l = it.next();
            if (l.entity.isRemoved() || l.entity.level() != l.level) {
                it.remove();
                continue;
            }
            if (l.leap < 0) {
                Player near = l.level.getNearestPlayer(l.spot.x, l.spot.y, l.spot.z, REVEAL, false);
                if (near == null) continue;
                l.leap = 0;
                l.from = l.entity.position();
                l.spot = landing(l.level, l.spot, l.from);
                WormholeSounds.rumble(l.level, l.from);
            }
            l.leap++;
            if (l.leap == RUMBLE_TICKS) WormholeSounds.cry(l.entity);
            if (l.leap == TAKE_OFF + 1 - JUMP_LEAD) animate(l.entity, JUMP);
            if (l.leap <= TAKE_OFF) continue;
            if (l.leap == TAKE_OFF + 1) l.entity.setNoGravity(true);
            if ((l.leap - TAKE_OFF - 1) % AIR_EVERY == 0) animate(l.entity, AIR);
            if (move(l, l.leap - TAKE_OFF)) {
                land(l);
                it.remove();
            }
        }
    }

    private static Vec3 landing(ServerLevel level, Vec3 spot, Vec3 from) {
        boolean alongX = Math.abs(spot.x - from.x) > Math.abs(spot.z - from.z);
        double forward = Math.signum(alongX ? spot.x - from.x : spot.z - from.z);
        int x = Mth.floor(spot.x), y = Mth.floor(spot.y), z = Mth.floor(spot.z);
        Integer low = null, high = null;
        for (int d = 1; d <= ARMS_SEARCH && (low == null || high == null); d++) {
            if (low == null && blocked(level, alongX ? x : x - d, y, alongX ? z - d : z)) low = (alongX ? z : x) - d;
            if (high == null && blocked(level, alongX ? x : x + d, y, alongX ? z + d : z)) high = (alongX ? z : x) + d;
        }
        double across = low != null && high != null ? (low + 1 + high) / 2.0 : (alongX ? spot.z : spot.x);
        Vec3 at = alongX ? new Vec3(spot.x - forward * BACK, spot.y, across) : new Vec3(across, spot.y, spot.z - forward * BACK);
        BlockPos under = BlockPos.containing(at.x, at.y - 0.5, at.z);
        if (level.getBlockState(under).getCollisionShape(level, under).isEmpty()
                || blocked(level, Mth.floor(at.x), y, Mth.floor(at.z))) return spot;
        return at;
    }

    private static boolean blocked(ServerLevel level, int x, int y, int z) {
        for (int up = 0; up < 2; up++) {
            BlockPos pos = new BlockPos(x, y + up, z);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return true;
        }
        return false;
    }

    private static boolean move(Leap l, int ticks) {
        int length = Math.max(1, (int) Math.ceil(l.from.distanceTo(l.spot) / LEAP_SPEED));
        double t = Math.min(1, ticks / (double) length);
        double arc = Math.max(4, 0.3 * (l.spot.y - l.from.y) + 6);
        WildsShadows.place(l.entity, l.from.lerp(l.spot, t).add(0, arc * 4 * t * (1 - t), 0), WildsShadows.facing(l.from, l.spot));
        return t >= 1;
    }

    private static void land(Leap l) {
        l.level.playSound(null, l.spot.x, l.spot.y, l.spot.z, SoundEvents.GENERIC_BIG_FALL, SoundSource.NEUTRAL, 2f, 0.7f);
        l.level.sendParticles(ParticleTypes.POOF, l.spot.x, l.spot.y + 0.2, l.spot.z, 24, 0.9, 0.1, 0.9, 0.06);
        Player look = l.level.getNearestPlayer(l.spot.x, l.spot.y, l.spot.z, -1, false);
        WildsShadows.place(l.entity, l.spot, look != null ? WildsShadows.facing(l.spot, look.position()) : l.entity.getYRot());
        l.entity.setNoGravity(false);
        l.entity.removeTag(TAG);
        animate(l.entity, LAND);
    }

    private static void animate(PokemonEntity entity, String animation) {
        String group = entity.getPokemon().getSpecies().getResourceIdentifier().getPath() + GROUP;
        entity.playAnimation("q.bedrock_primary('" + group + "', '" + animation + "', q.curve('one'))", List.of());
    }
}
