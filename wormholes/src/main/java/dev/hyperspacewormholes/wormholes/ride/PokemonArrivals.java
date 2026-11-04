package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.entity.WormholeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Pokemon coming through their own little wormhole. It tears open over their spot, they drop out and settle, then it
 * closes.
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
    private static final int PORTAL_HALF_WIDTH = 3;
    private static final int PORTAL_DEPTH = 4;

    private static final class Emergence {
        final ServerLevel level;
        final PokemonEntity entity;
        final Vec3 landing;
        final float yaw;
        final WormholeType type;
        final boolean rainbow;
        Vec3 from;
        boolean noGravityBefore;
        int age;

        Emergence(ServerLevel level, PokemonEntity entity, Vec3 landing, float yaw, WormholeType type, boolean rainbow) {
            this.level = level;
            this.entity = entity;
            this.landing = landing;
            this.yaw = yaw;
            this.type = type;
            this.rainbow = rainbow;
        }
    }

    private static final List<Emergence> ACTIVE = new ArrayList<>();

    private PokemonArrivals() {}

    static void start(ServerLevel level, PokemonEntity entity, Vec3 landing, float yaw, WormholeType type, boolean rainbow) {
        entity.setPos(landing);
        ACTIVE.add(new Emergence(level, entity, landing, yaw, type, rainbow));
    }

    public static void tick(MinecraftServer server) {
        for (Iterator<Emergence> it = ACTIVE.iterator(); it.hasNext(); ) {
            Emergence e = it.next();
            e.age++;
            if (e.age < PAUSE_TICKS) continue;
            if (e.age == PAUSE_TICKS) {
                open(e);
                continue;
            }
            int since = e.age - PAUSE_TICKS;
            if (since < OPEN_TICKS) continue;
            if (since == OPEN_TICKS) {
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
            float t = Math.min(1f, (since - OPEN_TICKS) / (float) DESCENT_TICKS);
            float eased = 1 - (1 - t) * (1 - t);
            e.entity.setPos(e.from.lerp(e.landing, eased));
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
        double drop = 0;
        for (double d = 0.5; d <= MAX_DROP; d += 0.5) {
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
        portal.setLifetime(OPEN_TICKS + DESCENT_TICKS + LINGER_TICKS + WormholeEntity.CLOSE_TICKS);
        double mouthY = e.from.y + e.entity.getBbHeight() + MOUTH_GAP;
        portal.moveTo(e.from.x, mouthY - WormholeEntity.SIZE / 2, e.from.z, e.yaw, FACING_DOWN);
        e.level.addFreshEntity(portal);
        WormholeSounds.open(e.level, new Vec3(e.from.x, mouthY, e.from.z), 0.5f, 1.9f);
    }

    private static void announce(Emergence e) {
        Pokemon pokemon = e.entity.getPokemon();
        e.level.playSound(null, e.from.x, e.from.y, e.from.z,
                SoundEvent.createVariableRangeEvent(LegendRoar.cryId(pokemon)), SoundSource.NEUTRAL, 3f, 1f);
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
            if (e.age - PAUSE_TICKS < OPEN_TICKS) {
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
