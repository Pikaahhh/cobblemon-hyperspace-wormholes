package dev.hyperspacewormholes.wormholes.zmove;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.FleeableBattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext;
import com.cobblemon.mod.common.api.moves.animations.EntityProvider;
import com.cobblemon.mod.common.api.moves.animations.TargetsProvider;
import com.cobblemon.mod.common.api.moves.animations.UsersProvider;
import com.cobblemon.mod.common.api.moves.animations.keyframes.ActionEffectKeyframe;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.boss.Boss;
import dev.hyperspacewormholes.wormholes.boss.Bosses;
import dev.hyperspacewormholes.wormholes.network.BurnsSkyPayload;
import dev.hyperspacewormholes.wormholes.network.BurnsSkyStopPayload;
import kotlin.Unit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Ultra Necrozma's Z-Move as a step in Cobblemon's move animation (lightthatburnsthesky.json holds the battle on this
 * keyframe). The server runs Ultra Sun's timeline (BurnsSkyTimeline), plays the game's sounds on its cues and tells players
 * within 192 blocks to draw it (BurnsSkyClient). It is one attack on the whole group, aimed at the middle of every
 * trainer's active Pokemon in the battles.
 * <p>
 * It starts only after Mega Showdown's Z-power activation (Z_ACTIVATION ticks from ZPOWER_USED); a boss's ultimate runs in
 * every battle with the Z-Power line only in the first, so a run that hasn't seen it yet waits the whole activation.
 * Necrozma retreats back and up with gravity off (BurnsSkyTimeline.retreat), shortened until it is clear of blocks and
 * keeps every trainer FLEE_MARGIN inside the flee distance, then glides home.
 */
public final class LightThatBurnsTheSky implements ActionEffectKeyframe {
    public static final String TYPE = "hyperspace_wormholes:light_that_burns_the_sky";

    public static final double HEAD_HEIGHT = 0.79;
    public static final double MOUTH_REACH = 0.35;
    private static final double VIEW_RANGE = 192;
    private static final int MAX_TICKS = 800;
    private static final float SOUND_RANGE = 120;
    private static final double FLEE_MARGIN = 4;
    public static final int Z_ACTIVATION = 160;

    private record Cue(int at, String sound, float pitch) {}

    private static final Cue[] SOUNDS = {
            cue(115, "c03c6", 1f), cue(158, "c03c7.a5a3b938", 1f), cue(243, "c03c8", 1f),
            cue(277, "c03c9.a85156a2", 1f), cue(372, "c03ca.338ac08b", 1f), cue(443, "c03c9.997b96e5", 1f),
            cue(454, "c03cc", 1f), cue(454, "c03cb.0e9bd64e", 1f), cue(494, "c03cd", 1f),
            cue(498, "c03ce.6745c9d0", 1f), cue(528, "c03cf", 1f), cue(567, "c03d0", 1f), cue(581, "c03d1", 1f),
            cue(640, "c03a5", 1f), cue(666, "c03d2", 1f), cue(690, "c03d3", 1f), cue(693, "c03d4", 1f),
            cue(722, "c03d5", 1f), cue(724, "c039a", 1f), cue(751, "c03b2.87b80dc9", 1.2756f), cue(754, "c039b", 1f),
            cue(785, "c039f", 1f), cue(807, "c03d6", 1f), cue(837, "c0396", 1f), cue(850, "c03d7", 1f),
            cue(863, "c039c", 1f)};

    private static Cue cue(float frame, String sound, float pitch) {
        return new Cue(BurnsSkyTimeline.at(frame), sound, pitch);
    }

    private static final double[][] WAYS = {{1, 1}, {0.75, 1}, {0.5, 1}, {0.25, 1}, {0, 1}, {0.75, 0.75}, {0.5, 0.5}, {0.25, 0.25}};
    private static final List<Run> RUNS = new ArrayList<>();
    private static final Map<UUID, Long> ZPOWER = new HashMap<>();

    private static final class Run {
        final PokemonEntity user;
        final ServerLevel level;
        final Vec3 dir, targetCentre, targetFeet, spot;
        final CompletableFuture<Unit> future = new CompletableFuture<>();
        final List<ServerPlayer> viewers;
        final double back, rise;
        final boolean noGravity;
        final int targetId;
        final long created;
        Vec3 placed;
        @Nullable Vec3 release;
        boolean moving;
        int age;
        @Nullable BlockPos light;
        @Nullable BlockState under;

        Run(PokemonEntity user, int targetId, Vec3 targetCentre, Vec3 targetFeet, ServerLevel level, Vec3 dir,
            List<ServerPlayer> viewers, double[] way, int wait) {
            this.user = user;
            this.targetId = targetId;
            this.age = -wait;
            this.created = level.getGameTime();
            this.level = level;
            this.dir = dir;
            this.targetCentre = targetCentre;
            this.targetFeet = targetFeet;
            this.viewers = viewers;
            this.spot = user.position();
            this.back = BurnsSkyTimeline.RETREAT_BACK * way[0];
            this.rise = BurnsSkyTimeline.retreatRise(user.getBbHeight()) * way[1];
            this.noGravity = user.isNoGravity();
            this.placed = spot;
            this.moving = way[0] > 0 || way[1] > 0;
        }
    }

    public static void register() {
        ActionEffectKeyframe.Companion.getTypes().put(TYPE, LightThatBurnsTheSky.class);
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        CobblemonEvents.ZPOWER_USED.subscribe(Priority.LOWEST, event -> {
            PokemonEntity entity = event.getPokemon().getEffectedPokemon().getEntity();
            if (entity == null) return Unit.INSTANCE;
            ZPOWER.put(entity.getUUID(), entity.level().getGameTime());
            for (Run run : RUNS) {
                if (run.user != entity || run.age >= 0) continue;
                run.age = Math.min(run.age, -Z_ACTIVATION);
                Wormholes.LOGGER.info("[burns-sky] Z-power for #{} while the move waits: {} more ticks", entity.getId(), -run.age);
            }
            return Unit.INSTANCE;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Run run : RUNS) {
                done(run);
                run.future.complete(Unit.INSTANCE);
            }
            RUNS.clear();
            ZPOWER.clear();
        });
    }

    @Override
    public CompletableFuture<Unit> play(ActionEffectContext context) {
        Entity user = first(context, UsersProvider.class);
        if (!(user instanceof PokemonEntity pokemon) || !(user.level() instanceof ServerLevel level)) {
            return CompletableFuture.completedFuture(Unit.INSTANCE);
        }
        for (Run run : RUNS) {
            if (run.user == pokemon && run.age < BurnsSkyTimeline.THROW) return run.future;
        }
        List<PokemonBattle> battles = battles(pokemon, context);
        List<Entity> targets = targets(pokemon, level, battles, context);
        if (targets.isEmpty()) return CompletableFuture.completedFuture(Unit.INSTANCE);

        Vec3 feet = Vec3.ZERO, centre = Vec3.ZERO;
        for (Entity target : targets) {
            feet = feet.add(target.position());
            centre = centre.add(target.getBoundingBox().getCenter());
        }
        feet = feet.scale(1.0 / targets.size());
        centre = centre.scale(1.0 / targets.size());
        Vec3 toTarget = feet.subtract(user.position());
        Vec3 dir = new Vec3(toTarget.x, 0, toTarget.z);
        dir = dir.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, user.getYRot()) : dir.normalize();

        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(user) < VIEW_RANGE * VIEW_RANGE) viewers.add(player);
        }
        String seen = zpowerSeen(pokemon);
        Run run = new Run(pokemon, targets.getFirst().getId(), centre, feet, level, dir, viewers,
                clearWay(pokemon, level, dir, battles), activationLeft(pokemon));
        if (run.moving) pokemon.setNoGravity(true);
        pokemon.getBusyLocks().add(run);
        Wormholes.LOGGER.info("[burns-sky] Light That Burns the Sky for #{} waits {} ticks for the Z-power activation ({})",
                user.getId(), -run.age, seen);
        if (run.age == 0) start(run);
        RUNS.add(run);
        return run.future;
    }

    private static long zpowerAge(PokemonEntity user) {
        Long at = ZPOWER.get(user.getUUID());
        long since = at == null ? -1 : user.level().getGameTime() - at;
        return since >= 0 && since < Z_ACTIVATION ? since : -1;
    }

    private static String zpowerSeen(PokemonEntity user) {
        long since = zpowerAge(user);
        return since < 0 ? "its Z-power not seen yet" : "Z-power " + since + " ticks ago";
    }

    private static int activationLeft(PokemonEntity user) {
        long since = zpowerAge(user);
        ZPOWER.remove(user.getUUID());
        return since < 0 ? Z_ACTIVATION : (int) (Z_ACTIVATION - since);
    }

    private static void start(Run run) {
        Wormholes.LOGGER.info("[burns-sky] Light That Burns the Sky for #{} starts after {} ticks", run.user.getId(),
                run.level.getGameTime() - run.created);
        send(run, new BurnsSkyPayload(run.user.getId(), run.targetId, mouth(run.user, run.dir), run.dir, run.targetCentre,
                run.targetFeet, BurnsSkyTimeline.HIT - BurnsSkyTimeline.THROW));
    }

    private static List<PokemonBattle> battles(PokemonEntity user, ActionEffectContext context) {
        Boss boss = Bosses.of(user);
        if (boss != null && !boss.battles().isEmpty()) return boss.battles();
        List<PokemonBattle> out = new ArrayList<>();
        for (Object provider : context.getProviders()) {
            if (provider instanceof PokemonBattle battle) out.add(battle);
        }
        return out;
    }

    private static List<Entity> targets(PokemonEntity user, ServerLevel level, List<PokemonBattle> battles,
                                        ActionEffectContext context) {
        List<Entity> out = new ArrayList<>();
        for (PokemonBattle battle : battles) {
            BattleSide own = null;
            for (BattleActor actor : battle.getActors()) {
                for (ActiveBattlePokemon active : actor.getActivePokemon()) {
                    BattlePokemon pokemon = active.getBattlePokemon();
                    if (pokemon != null && pokemon.getEntity() == user) own = actor.getSide();
                }
            }
            for (BattleActor actor : battle.getActors()) {
                if (actor.getType() == ActorType.WILD || actor.getSide() == own) continue;
                for (ActiveBattlePokemon active : actor.getActivePokemon()) {
                    BattlePokemon pokemon = active.getBattlePokemon();
                    PokemonEntity entity = pokemon == null ? null : pokemon.getEntity();
                    if (entity != null && entity != user && !entity.isRemoved() && entity.level() == level && !out.contains(entity)) {
                        out.add(entity);
                    }
                }
            }
        }
        if (!out.isEmpty()) return out;
        for (Object provider : context.getProviders()) {
            if (!(provider instanceof TargetsProvider targets)) continue;
            for (Entity entity : targets.getEntities()) {
                if (entity != user && entity.level() == level && !out.contains(entity)) out.add(entity);
            }
        }
        return out;
    }

    private static List<Entity> trainers(List<PokemonBattle> battles) {
        List<Entity> out = new ArrayList<>();
        for (PokemonBattle battle : battles) {
            for (BattleActor actor : battle.getActors()) {
                if (actor.getType() != ActorType.PLAYER || !(actor instanceof EntityBackedBattleActor<?> backed)) continue;
                Entity entity = backed.getEntity();
                if (entity != null && !out.contains(entity)) out.add(entity);
            }
        }
        return out;
    }

    private static double fleeReach(List<PokemonBattle> battles) {
        double reach = Double.MAX_VALUE;
        for (PokemonBattle battle : battles) {
            for (BattleActor actor : battle.getActors()) {
                if (actor instanceof FleeableBattleActor fleeable && fleeable.getFleeDistance() > 0) {
                    reach = Math.min(reach, fleeable.getFleeDistance() - FLEE_MARGIN);
                }
            }
        }
        return reach;
    }

    public static Vec3 head(Entity user) {
        return user.position().add(0, user.getBbHeight() * HEAD_HEIGHT, 0);
    }

    public static Vec3 mouth(Entity user, Vec3 dir) {
        return head(user).add(dir.scale(user.getBbWidth() * MOUTH_REACH));
    }

    private static @Nullable Entity first(ActionEffectContext context, Class<? extends EntityProvider> type) {
        for (Object provider : context.getProviders()) {
            if (type.isInstance(provider)) {
                List<Entity> entities = ((EntityProvider) provider).getEntities();
                if (!entities.isEmpty()) return entities.getFirst();
            }
        }
        return null;
    }

    private static Vec3 ball(Run run) {
        if (run.age < BurnsSkyTimeline.THROW || run.release == null) {
            return held(run);
        }
        return BurnsSkyTimeline.thrown(run.age, run.release, run.targetFeet);
    }

    private static Vec3 held(Run run) {
        return BurnsSkyTimeline.held(run.age, mouth(run.user, run.dir), run.dir);
    }

    private static double[] clearWay(Entity user, ServerLevel level, Vec3 dir, List<PokemonBattle> battles) {
        double rise = BurnsSkyTimeline.retreatRise(user.getBbHeight());
        List<Entity> trainers = trainers(battles);
        double reach = fleeReach(battles);
        for (double[] way : WAYS) {
            boolean clear = true;
            for (int i = 1; i <= 16 && clear; i++) {
                double age = BurnsSkyTimeline.RETREAT_FROM + (BurnsSkyTimeline.RETREAT_TO - BurnsSkyTimeline.RETREAT_FROM) * i / 16.0;
                Vec3 off = BurnsSkyTimeline.retreat(age, dir, BurnsSkyTimeline.RETREAT_BACK * way[0], rise * way[1]);
                clear = level.noCollision(user, user.getBoundingBox().move(off));
                Vec3 at = user.position().add(off);
                for (Entity trainer : trainers) {
                    if (trainer.level() == level && trainer.position().distanceTo(at) >= reach) clear = false;
                }
            }
            if (clear) return way;
        }
        return new double[]{0, 0};
    }

    private static void drive(Run run) {
        if (!run.moving) return;
        if (run.user.isRemoved() || run.user.position().distanceToSqr(run.placed) > 0.25) {
            run.moving = false;
            run.user.getBusyLocks().remove(run);
            if (!run.user.isRemoved()) run.user.setNoGravity(run.noGravity);
            return;
        }
        Vec3 to = run.spot.add(BurnsSkyTimeline.retreat(run.age, run.dir, run.back, run.rise));
        run.user.setPos(to.x, to.y, to.z);
        run.user.setDeltaMovement(Vec3.ZERO);
        run.placed = to;
    }

    private static void face(Run run) {
        if (run.user.isRemoved()) return;
        float yaw = (float) (Mth.atan2(-run.dir.x, run.dir.z) * Mth.RAD_TO_DEG);
        run.user.setYRot(yaw);
        run.user.setYHeadRot(yaw);
        run.user.setYBodyRot(yaw);
    }

    private static void done(Run run) {
        clearLight(run);
        home(run);
        run.user.getBusyLocks().remove(run);
    }

    private static void home(Run run) {
        if (!run.moving) return;
        run.moving = false;
        if (run.user.isRemoved()) return;
        run.user.setPos(run.spot.x, run.spot.y, run.spot.z);
        run.user.setNoGravity(run.noGravity);
    }

    private static void moveLight(Run run) {
        double radius = BurnsSkyTimeline.ballRadius(run.age);
        if (radius <= 0 || run.age >= BurnsSkyTimeline.HIT) {
            clearLight(run);
            return;
        }
        BlockPos pos = BlockPos.containing(ball(run).subtract(0, radius * BurnsSkyTimeline.BALL_OUTER, 0));
        if (pos.equals(run.light)) return;
        clearLight(run);
        BlockState here = run.level.getBlockState(pos);
        if (!here.isAir()) return;
        run.level.setBlockAndUpdate(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
        run.light = pos;
        run.under = here;
    }

    private static void clearLight(Run run) {
        if (run.light != null && run.level.getBlockState(run.light).is(Blocks.LIGHT)) {
            run.level.setBlockAndUpdate(run.light, run.under != null ? run.under : Blocks.AIR.defaultBlockState());
        }
        run.light = null;
        run.under = null;
    }

    private static void tick() {
        for (Iterator<Run> it = RUNS.iterator(); it.hasNext(); ) {
            Run run = it.next();
            run.age++;
            if (run.age < BurnsSkyTimeline.HIT && (run.user.isRemoved() || run.age > MAX_TICKS)) {
                done(run);
                send(run, new BurnsSkyStopPayload(run.user.getId()));
                run.future.complete(Unit.INSTANCE);
                it.remove();
                continue;
            }
            if (run.age == 0) start(run);
            drive(run);
            if (run.age == BurnsSkyTimeline.THROW && !run.user.isRemoved()) {
                run.release = held(run);
            }
            if (run.age < BurnsSkyTimeline.END || run.moving) face(run);
            sounds(run);
            moveLight(run);
            if (run.age >= BurnsSkyTimeline.END) {
                clearLight(run);
                run.future.complete(Unit.INSTANCE);
            }
            if (run.age >= BurnsSkyTimeline.RETURN_TO || run.age > MAX_TICKS || run.age >= BurnsSkyTimeline.END && !run.moving) {
                done(run);
                run.future.complete(Unit.INSTANCE);
                it.remove();
            }
        }
    }

    private static void sounds(Run run) {
        for (Cue cue : SOUNDS) {
            if (cue.at() != run.age) continue;
            Vec3 at = run.age < BurnsSkyTimeline.BALL ? run.user.position()
                    : run.age < BurnsSkyTimeline.HIT ? ball(run) : run.targetFeet;
            run.level.playSound(null, at.x, at.y, at.z,
                    SoundEvent.createFixedRangeEvent(Wormholes.id("usum.se." + cue.sound()), SOUND_RANGE), SoundSource.NEUTRAL,
                    1f, cue.pitch());
        }
    }

    private static void send(Run run, CustomPacketPayload payload) {
        for (ServerPlayer player : run.viewers) {
            if (!player.hasDisconnected() && player.level() == run.level) ServerPlayNetworking.send(player, payload);
        }
    }
}
