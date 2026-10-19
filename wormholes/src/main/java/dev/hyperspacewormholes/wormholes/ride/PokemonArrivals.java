package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.ZCrystals;
import dev.hyperspacewormholes.wormholes.entity.WormholeEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Pokemon coming through their own little wormhole. It tears open over their spot, they drop or float out and settle,
 * then it closes. Also sky wormholes flinging Pokemon down to the ground.
 */
public final class PokemonArrivals {
    private static final int PAUSE_TICKS = 40;
    private static final int OPEN_TICKS = 15;
    private static final int DESCENT_TICKS = 50;
    private static final int LINGER_TICKS = 10;
    static final int TICKS_UNTIL_CLOSED = PAUSE_TICKS + OPEN_TICKS + DESCENT_TICKS + LINGER_TICKS + WormholeEntity.CLOSE_TICKS;
    private static final double MAX_DROP = 7;
    private static final double MOUTH_GAP = 0.4;
    private static final float FACING_DOWN = 90f;
    private static final double LAUNCH_SPEED = 1.3;
    private static final int LAUNCH_MIN_TICKS = 14;
    private static final double LAUNCH_ARC = 0.12;
    private static final int LAUNCH_OPEN_TICKS = 70;
    private static final int LAUNCH_SPACING = 12;
    private static final double ANNOUNCE_RANGE = 128;
    private static final int PORTAL_HALF_WIDTH = 3;
    private static final int PORTAL_DEPTH = 4;

    private static final class Emergence {
        final ServerLevel level;
        final PokemonEntity entity;
        final Vec3 landing;
        final float yaw;
        final WormholeType type;
        final int delay;
        final boolean high;
        final boolean rainbow;
        Vec3 from;
        boolean noGravityBefore;
        int age;
        @Nullable Vec3 launch;
        boolean opensPortal = true;
        float portalYaw, portalPitch;
        int portalLife;

        Emergence(ServerLevel level, PokemonEntity entity, Vec3 landing, float yaw, WormholeType type, int delay, boolean high,
                  boolean rainbow) {
            this.level = level;
            this.entity = entity;
            this.landing = landing;
            this.yaw = yaw;
            this.type = type;
            this.delay = delay;
            this.high = high;
            this.rainbow = rainbow;
        }

        int openTicks() {
            return launch != null ? LAUNCH_OPEN_TICKS : OPEN_TICKS;
        }

        int descentTicks() {
            if (launch != null) return Math.max(LAUNCH_MIN_TICKS, (int) Math.ceil(launch.distanceTo(landing) / LAUNCH_SPEED));
            if (!high) return DESCENT_TICKS;
            return Math.max(1, (int) Math.ceil((from.y - landing.y) / WormholeEntity.SINK_SPEED));
        }
    }

    private static final List<Emergence> ACTIVE = new ArrayList<>();

    private PokemonArrivals() {}

    static void start(ServerLevel level, PokemonEntity entity, Vec3 landing, float yaw, WormholeType type, boolean high,
                      boolean rainbow) {
        entity.setPos(landing);
        ZCrystals.mark(entity.getPokemon());
        ACTIVE.add(new Emergence(level, entity, landing, yaw, type, PAUSE_TICKS, high, rainbow));
    }

    static void launch(ServerLevel level, List<PokemonEntity> entities, List<Vec3> landings, List<Float> yaws, Vec3 mouth,
                       float facing, float pitch, WormholeType type) {
        int longest = 0;
        List<Emergence> all = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) {
            PokemonEntity entity = entities.get(i);
            entity.setPos(landings.get(i));
            ZCrystals.mark(entity.getPokemon());
            Emergence e = new Emergence(level, entity, landings.get(i), yaws.get(i), type, PAUSE_TICKS + i * LAUNCH_SPACING,
                    false, false);
            e.launch = mouth;
            e.opensPortal = i == 0;
            longest = Math.max(longest, i * LAUNCH_SPACING + e.descentTicks());
            all.add(e);
        }
        all.get(0).portalYaw = facing;
        all.get(0).portalPitch = pitch;
        all.get(0).portalLife = LAUNCH_OPEN_TICKS + longest + LINGER_TICKS + WormholeEntity.CLOSE_TICKS;
        ACTIVE.addAll(all);
    }

    public static void tick(MinecraftServer server) {
        for (Iterator<Emergence> it = ACTIVE.iterator(); it.hasNext(); ) {
            Emergence e = it.next();
            e.age++;
            if (e.age < e.delay) continue;
            if (e.age == e.delay) {
                open(e);
                continue;
            }
            int since = e.age - e.delay;
            if (since < e.openTicks()) continue;
            if (since == e.openTicks()) {
                e.entity.moveTo(e.from.x, e.from.y, e.from.z, e.yaw, 0f);
                e.noGravityBefore = e.entity.isNoGravity();
                e.entity.setNoGravity(true);
                e.level.addFreshEntity(e.entity);
                e.level.playSound(null, e.from.x, e.from.y, e.from.z, SoundEvents.PORTAL_TRAVEL, SoundSource.NEUTRAL, 0.3f, 1.8f);
                if (e.type == WormholeType.GOLD || e.type == WormholeType.WHITE) announce(e);
                continue;
            }
            if (e.entity.isRemoved() || e.entity.level() != e.level) {
                it.remove();
                continue;
            }
            float t = Math.min(1f, (since - e.openTicks()) / (float) e.descentTicks());
            if (e.launch != null) {
                Vec3 at = e.launch.lerp(e.landing, t).add(0, e.launch.distanceTo(e.landing) * LAUNCH_ARC * 4 * t * (1 - t), 0);
                Vec3 heading = e.landing.subtract(e.launch);
                float flying = (float) (Mth.atan2(-heading.x, heading.z) * Mth.RAD_TO_DEG);
                e.entity.setPos(at);
                e.entity.setDeltaMovement(Vec3.ZERO);
                e.entity.setYRot(flying);
                e.entity.setYBodyRot(flying);
                e.entity.fallDistance = 0;
                if (t >= 1) {
                    e.level.playSound(null, e.landing.x, e.landing.y, e.landing.z, SoundEvents.GENERIC_BIG_FALL, SoundSource.NEUTRAL, 1.5f, 0.8f);
                    e.level.sendParticles(ParticleTypes.POOF, e.landing.x, e.landing.y + 0.2, e.landing.z, 16, 0.6, 0.1, 0.6, 0.05);
                    e.entity.setNoGravity(e.noGravityBefore);
                    it.remove();
                }
                continue;
            }
            if (e.high) {
                double y = Math.max(e.landing.y, e.from.y - (since - e.openTicks()) * WormholeEntity.SINK_SPEED);
                e.entity.setPos(e.landing.x, y, e.landing.z);
            } else {
                float eased = 1 - (1 - t) * (1 - t);
                e.entity.setPos(e.from.lerp(e.landing, eased));
            }
            e.entity.setDeltaMovement(Vec3.ZERO);
            e.entity.setYRot(e.yaw);
            e.entity.setYBodyRot(e.yaw);
            e.entity.fallDistance = 0;
            if (t >= 1) {
                e.entity.setNoGravity(e.noGravityBefore);
                it.remove();
            }
        }
    }

    private static void open(Emergence e) {
        if (e.launch != null) {
            e.from = e.launch;
            if (!e.opensPortal) return;
            WormholeEntity portal = Wormholes.WORMHOLE.create(e.level);
            portal.setDecorative(true);
            portal.setWormholeType(e.type);
            portal.setScale(2f);
            portal.setLifetime(e.portalLife);
            portal.moveTo(e.from.x, e.from.y - WormholeEntity.SIZE / 2, e.from.z, e.portalYaw, e.portalPitch);
            e.level.addFreshEntity(portal);
            WormholeSounds.open(e.level, e.from, 3f, 1.6f);
            return;
        }
        double drop = 0;
        double maxDrop = e.high ? WormholeDestinations.floatHeight(e.level, e.landing, PORTAL_HALF_WIDTH + 1, MAX_DROP) : MAX_DROP;
        for (double d = 0.5; d <= maxDrop; d += 0.5) {
            e.entity.setPos(e.landing.add(0, d, 0));
            if (!e.level.noCollision(e.entity)) break;
            drop = d;
        }
        e.entity.setPos(e.landing);
        double height = e.entity.getBbHeight() + MOUTH_GAP;
        while (drop > 0 && !portalClear(e.level, e.landing.add(0, drop + height, 0))) drop -= 0.5;
        drop = Math.max(0, drop);
        Vec3 mouth = e.landing.add(0, drop + height, 0);
        if (!portalClear(e.level, mouth)) clearPortal(e.level, mouth);
        e.from = e.landing.add(0, drop, 0);

        WormholeEntity portal = Wormholes.WORMHOLE.create(e.level);
        portal.setDecorative(true);
        portal.setWormholeType(e.type);
        portal.setRainbow(e.rainbow);
        portal.setLifetime(OPEN_TICKS + e.descentTicks() + LINGER_TICKS + WormholeEntity.CLOSE_TICKS);
        double mouthY = e.from.y + e.entity.getBbHeight() + MOUTH_GAP;
        portal.moveTo(e.from.x, mouthY - WormholeEntity.SIZE / 2, e.from.z, e.yaw, FACING_DOWN);
        e.level.addFreshEntity(portal);
        WormholeSounds.open(e.level, new Vec3(e.from.x, mouthY, e.from.z), 0.5f, 1.9f);
    }

    private static void announce(Emergence e) {
        Pokemon pokemon = e.entity.getPokemon();
        e.level.playSound(null, e.from.x, e.from.y, e.from.z,
                SoundEvent.createVariableRangeEvent(LegendRoar.cryId(pokemon)), SoundSource.NEUTRAL, 3f, 1f);
        String article = "AEIOU".indexOf(Character.toUpperCase(pokemon.getSpecies().getName().charAt(0))) >= 0 ? "An" : "A";
        Component message = Component.translatable("hyperspace_wormholes.msg.special_appeared",
                article, pokemon.getSpecies().getTranslatedName()).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : e.level.players()) {
            if (player.distanceToSqr(e.from) <= ANNOUNCE_RANGE * ANNOUNCE_RANGE) player.sendSystemMessage(message);
        }
    }

    static boolean portalClear(ServerLevel level, Vec3 mouth) {
        for (BlockPos pos : portalBlocks(mouth)) {
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty()) return false;
            if (!state.getFluidState().isEmpty() && !UltraWarpRules.isUnderwater(level)) return false;
        }
        return true;
    }

    private static void clearPortal(ServerLevel level, Vec3 mouth) {
        for (BlockPos pos : portalBlocks(mouth)) {
            if (LandingClearing.isClearableGrowth(level.getBlockState(pos))) level.setBlockAndUpdate(pos, LandingClearing.emptyBlock(level));
        }
    }

    private static Iterable<BlockPos> portalBlocks(Vec3 mouth) {
        BlockPos c = BlockPos.containing(mouth);
        return BlockPos.betweenClosed(c.offset(-PORTAL_HALF_WIDTH, 0, -PORTAL_HALF_WIDTH),
                c.offset(PORTAL_HALF_WIDTH, PORTAL_DEPTH, PORTAL_HALF_WIDTH));
    }

    public static void finishAll() {
        for (Emergence e : ACTIVE) {
            if (e.age - e.delay < e.openTicks()) {
                e.entity.moveTo(e.landing.x, e.landing.y, e.landing.z, e.yaw, 0f);
                e.level.addFreshEntity(e.entity);
            } else if (!e.entity.isRemoved()) {
                e.entity.setPos(e.landing);
                e.entity.setNoGravity(e.noGravityBefore);
            }
        }
        ACTIVE.clear();
    }
}
