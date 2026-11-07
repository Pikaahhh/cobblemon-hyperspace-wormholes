package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.math.Transformation;
import dev.hyperspacewormholes.hyperspace.mixin.DisplayAccessor;
import dev.hyperspacewormholes.hyperspace.mixin.ItemDisplayAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleFunction;

/**
 * Hoopa throws a ring that opens into a portal, and a Pokemon steps out of it, as in Hoopa and the Clash of Ages.
 * The ring grows from a hoop into a square copy of Hoopa's waist ring (item hoopa_portal_closed) filled with the pink
 * swirl (hoopa_portal), then shrinks and flies back. Clients clip the Pokemon at the ring's plane (HoopaClip) so it
 * seems to come out of the swirl.
 */
public class HoopaSummon {
    private static final String TAG = "hyperspace_wormholes_hoopa_summon";

    /**
     * Per-form throw data: clip names, the ticks the ring leaves and is caught, and the ring's start and end points
     * (model px: left, up, ahead of the feet, before scale). With no catch clip the ring just vanishes at the horn.
     */
    private record Thrower(String summon, String hold, String grab, int throwAt, int clipEnd, double[] ringFrom,
                           double[] ringTo, float ringPx, int catchAt, int catchEnd) {
    }

    private static final Thrower CONFINED = new Thrower("summon", null, null, 83, 99, HoopaFeed.RING_FROM, HoopaFeed.RING_FROM,
            HoopaFeed.RING_PX, 0, 0);

    private static final int CHARGE = 4, HOLD_EARLY = 3;
    private static final int GROW = 8, SETTLE = 4, FILL = 13, OUT_AT = 22, CRY_AFTER = 2, CLOSE_AFTER = 16, SHRINK = 12;
    private static final int LAND_AFTER = 4, LAND = 30, MIN_EMERGE = 30, CLIP_OFF_AFTER = 3;
    private static final double MIN_INSIDE = 1.8, ROOM = 1.4, PAD = 0.8;
    private static final double LEAP = 0.3, START_BEHIND = 0.3;
    private static final double DROP_SPEED = 0.2, ABOVE_HEAD = 3, ABOVE_HEIGHT = 0.6;
    private static final double HOVER_DRIFT = 0.5, BOB = 0.1;
    // ring model at scale 1, in blocks: inner width, centre to the middle of a bar
    private static final float INSIDE = 36.4f / 16f, RIM = 20.8f / 16f;
    // outer width at scale 1; HOOP is the 0.45 block hoop shown when no Hoopa is around
    private static final float WIDTH = 46.8f / 16f, HOOP = 0.45f / WIDTH;
    // how far past its size a ring overshoots opening and swells closing
    private static final float SPRING = 1.4f;
    private static final double EASE = 1.6;
    private static final double BACK_SPEED = 1.1;
    private static final int BACK_MIN = 6, BACK_MAX = 18;
    private static final double FLY_SPIN = 16, THROW_OUT = 0.42, THROW_LIFT = 0.12;
    static final int POSE = 8;
    static final Quaternionfc FLAT = new Quaternionf().rotationX((float) Math.PI / 2), UPRIGHT = new Quaternionf();

    private static final List<Run> runs = new ArrayList<>();
    private static final List<Resize> resizes = new ArrayList<>();
    private static final List<Flight> flights = new ArrayList<>();
    private static final Set<UUID> ours = new HashSet<>();

    /**
     * A wrist ring as Hoopa wears it at a moment of its clip, facing yaw 0: position (model px: left, up, ahead),
     * forearm direction and ring turn. Thrown rings leave along the forearm and come back the same way.
     */
    public record Wrist(double[] at, double[] fore, float[] turn) {
    }

    public record Track(int entity, float[] poses) implements CustomPacketPayload {
        public static final Type<Track> TYPE = new Type<>(Hyperspace.id("hoopa_track"));
        public static final StreamCodec<FriendlyByteBuf, Track> CODEC = CustomPacketPayload.codec(Track::write, Track::read);

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entity);
            buf.writeVarInt(poses.length);
            for (float number : poses) buf.writeFloat(number);
        }

        private static Track read(FriendlyByteBuf buf) {
            int entity = buf.readVarInt();
            float[] poses = new float[buf.readVarInt()];
            for (int i = 0; i < poses.length; i++) poses[i] = buf.readFloat();
            return new Track(entity, poses);
        }

        @Override
        public Type<Track> type() {
            return TYPE;
        }
    }

    public record Clip(int entity, double x, double y, double z, float nx, float ny, float nz, boolean on) implements CustomPacketPayload {
        public static final Type<Clip> TYPE = new Type<>(Hyperspace.id("hoopa_clip"));
        public static final StreamCodec<FriendlyByteBuf, Clip> CODEC = CustomPacketPayload.codec(Clip::write, Clip::read);

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entity);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeFloat(nx);
            buf.writeFloat(ny);
            buf.writeFloat(nz);
            buf.writeBoolean(on);
        }

        private static Clip read(FriendlyByteBuf buf) {
            return new Clip(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readBoolean());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static class Run {
        ServerLevel level;
        PokemonEntity hoopa, summoned;
        Thrower thrower = CONFINED;
        Vec3 centre, normal, hoopaSpot, ringFrom, bend, out, in, hovered, landFrom, landTo, backFrom, backTo;
        Wrist off, on;
        float hoopaYaw, ringSize, scale, facing, hoop = HOOP;
        int age = -1, arrive, emerge, done, closeAt, backAt = -1, backTicks, home = -1, landAt = -1, land = -1;
        boolean noGravity, flier, released, clipped, keep, above;
        Display.ItemDisplay thrown, portal;
    }

    private record Resize(Display display, float to, int ticks, Quaternionfc turn, int[] age) {
    }

    private record Flight(ServerLevel level, Display.ItemDisplay ring, Vec3 from, Vec3 to, Vec3 bend, int ticks, boolean homing, @Nullable Runnable done, int[] age) {
    }

    public static final class Summoning {
        private final Run run;

        private Summoning(Run run) {
            this.run = run;
        }

        public boolean out() {
            return run.age >= run.done;
        }

        public boolean over() {
            return !runs.contains(run);
        }

        public PokemonEntity summoned() {
            return run.summoned;
        }

        public int landing() {
            return run.closeAt + SHRINK + backTicks(run, hand(run, run.hoopaSpot, run.hoopa.yBodyRot, run.on.at()));
        }

        public void landAt(int age) {
            run.land = age;
        }
    }

    /**
     * Fight version (HoopaEvent): the ring lies flat at ringY over ground, is thrown throwAt ticks from now, and the
     * Pokemon drops out and is held with no AI. The ring then flies home to the wrist.
     */
    public static Summoning ringIn(PokemonEntity hoopa, PokemonEntity summoned, Vec3 ground, Vec3 toward, int throwAt,
                                   Wrist off, Wrist on, float ringPx, Vec3 thrownFrom, double ringY, double inside) {
        Run run = new Run();
        run.level = (ServerLevel) hoopa.level();
        run.summoned = summoned;
        run.keep = true;
        run.flier = false;
        run.above = true;
        run.scale = (float) (inside / INSIDE);
        Vec3 flat = new Vec3(toward.x - ground.x, 0, toward.z - ground.z);
        run.normal = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        run.facing = HoopaFeed.yawTo(Vec3.ZERO, run.normal);
        run.normal = new Vec3(0, -1, 0);
        run.centre = new Vec3(ground.x, ringY, ground.z);
        run.in = run.centre.add(0, START_BEHIND, 0);
        run.out = ground;
        run.hoopa = hoopa;
        run.thrower = new Thrower(null, null, null, throwAt, -1, off.at(), on.at(), ringPx, 0, 0);
        run.off = off;
        run.on = on;
        run.hoopaSpot = thrownFrom;
        run.hoopaYaw = HoopaFeed.yawTo(run.hoopaSpot, run.centre);
        run.ringSize = (float) (ringPx * hoopaScale(hoopa));
        run.hoop = hoopFor(run.ringSize);
        run.ringFrom = hand(run, run.hoopaSpot, hoopa.yBodyRot, off.at());
        int flight = Mth.clamp((int) Math.round(run.ringFrom.distanceTo(run.centre) / HoopaFeed.RING_SPEED), 5, 30);
        run.arrive = throwAt + flight;
        int emergeTicks = Math.max(MIN_EMERGE, (int) Math.round(run.in.distanceTo(run.out) / DROP_SPEED));
        run.emerge = run.arrive + GROW + SETTLE + OUT_AT;
        run.done = run.emerge + emergeTicks;
        run.closeAt = run.done + CLOSE_AFTER;
        runs.add(run);
        return new Summoning(run);
    }

    public static double insideFor(PokemonEntity summoned) {
        return Math.max(MIN_INSIDE, Math.max(summoned.getBbWidth(), summoned.getBbHeight() * ABOVE_HEIGHT) * ROOM + PAD);
    }

    public static double outsideFor(double inside) {
        return inside / INSIDE * WIDTH;
    }

    public static double aboveFor(double height) {
        return height + ABOVE_HEAD;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Clip.TYPE, Clip.CODEC);
        PayloadTypeRegistry.playS2C().register(Track.TYPE, Track.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(HoopaSummon::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.getTags().contains(TAG) && !ours.contains(entity.getUUID())) entity.discard();
        });
    }

    static float hoopFor(float ringSize) {
        return ringSize / WIDTH;
    }

    static double hoopaScale(PokemonEntity hoopa) {
        return hoopa.getPokemon().getForm().getBaseScale() * hoopa.getPokemon().getEffectiveScale() / 16;
    }

    static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(Vec3.fromRGB24(rgb).toVector3f(), size);
    }

    private static Vec3 hand(Run run, Vec3 spot, float yaw, double[] from) {
        return spot.add(handWay(yaw, from).scale(hoopaScale(run.hoopa)));
    }

    private static Vec3 handWay(float yaw, double[] way) {
        Vec3 ahead = Vec3.directionFromRotation(0, yaw);
        Vec3 left = new Vec3(ahead.z, 0, -ahead.x);
        return left.scale(way[0]).add(0, way[1], 0).add(ahead.scale(way[2]));
    }

    private static Quaternionf turnOf(float yaw, Wrist wrist) {
        float[] t = wrist.turn();
        return turned(yaw, new Quaternionf(t[0], t[1], t[2], t[3]));
    }

    private static int backTicks(Run run, Vec3 to) {
        return Mth.clamp((int) Math.round(run.centre.distanceTo(to) / BACK_SPEED), BACK_MIN, BACK_MAX);
    }

    static Vec3 ground(ServerLevel level, Vec3 at) {
        BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
        for (int dy = 3; dy >= -6; dy--) {
            block.set(Mth.floor(at.x), Mth.floor(at.y) + dy - 1, Mth.floor(at.z));
            var shape = level.getBlockState(block).getCollisionShape(level, block);
            if (shape.isEmpty()) continue;
            BlockPos above = block.above();
            if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) continue;
            return new Vec3(at.x, block.getY() + shape.max(Direction.Axis.Y), at.z);
        }
        return null;
    }

    private static void tick(MinecraftServer server) {
        flights.removeIf(HoopaSummon::flying);
        resizes.removeIf(HoopaSummon::resizing);
        for (Iterator<Run> it = runs.iterator(); it.hasNext(); ) {
            Run run = it.next();
            if (!step(run)) {
                finish(run);
                it.remove();
            }
        }
    }

    private static boolean step(Run run) {
        run.age++;
        int age = run.age;
        ServerLevel level = run.level;
        if (run.hoopa != null && (run.hoopa.isRemoved() || run.hoopa.level() != level)) run.hoopa = null;
        Thrower th = run.thrower;
        if (run.hoopa != null) {
            if (age == 0 && th.summon != null) HoopaFeed.send(run.hoopa, th.summon, run.hoopaYaw);
            // a little before summon ends, so the clip never runs out into the idle
            if (th.hold != null && age == th.clipEnd - HOLD_EARLY) HoopaFeed.send(run.hoopa, th.hold, run.hoopaYaw);
            // Confined carries on after its clip; Unbound stays put until it has its ring back
            if (age <= th.clipEnd || th.hold != null) hold(run);
            if (age < th.throwAt && !run.keep) charge(run, age);
            if (age == th.throwAt) throwRing(run);
        }
        if (run.thrown != null && run.backAt < 0) flyRing(run);

        int since = age - run.arrive;
        if (since == 0) openRing(run);
        if (since == 1) resize(run.portal, run.hoop, run.scale, GROW + SETTLE, true, run.above ? FLAT : UPRIGHT);
        if (since > 0 && since < GROW + SETTLE) rimSparkle(run, 2);
        if (since == GROW + SETTLE + FILL - 6) {
            level.playSound(null, run.centre.x, run.centre.y, run.centre.z, SoundEvents.PORTAL_TRIGGER, SoundSource.NEUTRAL, 0.35f, 1.9f);
        }
        if (since == GROW + SETTLE + FILL) fill(run);

        PokemonEntity p = run.summoned;
        if (age == run.emerge) bringOut(run);
        boolean here = p != null && !p.isRemoved() && age >= run.emerge;
        if (here && age > run.emerge && age <= run.done) carry(run, (age - run.emerge) / (float) (run.done - run.emerge));
        if (age > run.emerge && age < run.done && age % 2 == 0) rimSparkle(run, 3);
        if (age == run.done) {
            if (run.flier) run.hovered = here ? p.position() : run.out;
            else if (run.keep) run.released = true;
            else release(run);
            if (here) level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + p.getBbHeight() / 2, p.getZ(), 8, 0.4, 0.4, 0.4, 0.05);
            level.playSound(null, run.centre.x, run.centre.y, run.centre.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 1.2f);
        }
        if (age == run.done + CRY_AFTER && here) p.cry();
        // walkers are let go at once and may wander off, so their clip ends as soon as they are through
        if (!run.flier && age == run.done + CLIP_OFF_AFTER) clip(run, false);
        if (run.flier && here && run.hovered != null && run.landAt < 0) hover(run);

        int close = age - run.closeAt;
        if (close == 0) {
            if (run.flier) clip(run, false);
            item(run.portal, ModItems.HOOPA_PORTAL_CLOSED);
            resize(run.portal, run.scale, run.hoop, SHRINK, false, run.above ? FLAT : UPRIGHT);
            level.playSound(null, run.centre.x, run.centre.y, run.centre.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 0.6f, 1.6f);
        }
        if (close == SHRINK) goHome(run);
        if (run.backAt >= 0 && run.home < 0) flyHome(run);

        if (run.flier && run.home >= 0 && run.landAt < 0 && age >= run.home + LAND_AFTER) startLanding(run);
        if (run.landAt >= 0 && !run.released) land(run, (age - run.landAt) / (float) LAND);

        boolean caught = run.home >= 0 && age >= run.home + Math.max(0, th.catchEnd - th.catchAt);
        boolean settled = run.released || p == null || p.isRemoved();
        return !(caught && settled);
    }

    private static void charge(Run run, int age) {
        Thrower th = run.thrower;
        int sparkA = th.throwAt * 23 / 83, sparkB = th.throwAt * 58 / 83;
        ServerLevel level = run.level;
        PokemonEntity hoopa = run.hoopa;
        Vec3 middle = hoopa.position().add(0, hoopa.getBbHeight() / 2, 0);
        if (age == CHARGE) {
            level.sendParticles(dust(0xFFE145, 1f), middle.x, middle.y, middle.z, 16,
                    hoopa.getBbWidth() * 0.6, hoopa.getBbHeight() * 0.5, hoopa.getBbWidth() * 0.6, 0);
            level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 0.8f, 1.5f);
        }
        if (age > CHARGE && age < sparkA && age % 3 == 0) {
            level.sendParticles(dust(age % 6 == 0 ? 0xFFF3B0 : 0xF9C539, 1f), middle.x, middle.y, middle.z, 6,
                    hoopa.getBbWidth() * 0.5, hoopa.getBbHeight() * 0.45, hoopa.getBbWidth() * 0.5, 0);
        }
        if (age == sparkA || age == sparkB) {
            Vec3 head = hoopa.position().add(0, hoopa.getBbHeight() * 0.85, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, head.x, head.y, head.z, 6, 0.35, 0.2, 0.35, 0.05);
            level.playSound(null, head.x, head.y, head.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.NEUTRAL, 0.7f, age == sparkB ? 1.6f : 1.2f);
        }
    }

    private static void hold(Run run) {
        PokemonEntity hoopa = run.hoopa;
        hoopa.getNavigation().stop();
        hoopa.setDeltaMovement(Vec3.ZERO);
        hoopa.setPos(run.hoopaSpot);
        hoopa.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        hoopa.setYRot(run.hoopaYaw);
        hoopa.setYBodyRot(run.hoopaYaw);
        hoopa.setYHeadRot(run.hoopaYaw);
        hoopa.yRotO = run.hoopaYaw;
        hoopa.yBodyRotO = run.hoopaYaw;
        hoopa.yHeadRotO = run.hoopaYaw;
        hoopa.setXRot(0);
    }

    private static Display.ItemDisplay ringDisplay(ServerLevel level, Vec3 at, Quaternionfc turn, float size) {
        Display.ItemDisplay ring = EntityType.ITEM_DISPLAY.create(level);
        if (ring == null) return null;
        ring.setPos(at.x, at.y, at.z);
        ((ItemDisplayAccessor) ring).hyperspace_wormholes$setItem(new ItemStack(ModItems.HOOPA_THROWN_RING));
        ((DisplayAccessor) ring).hyperspace_wormholes$setBrightness(new Brightness(15, 15));
        ((DisplayAccessor) ring).hyperspace_wormholes$setTransformation(pose(turn, size));
        add(level, ring);
        return ring;
    }

    private static void throwRing(Run run) {
        Quaternionf held;
        if (run.off != null) {
            float yaw = run.hoopa.yBodyRot;
            held = turnOf(yaw, run.off);
            run.bend = run.ringFrom.add(handWay(yaw, run.off.fore()).scale(run.ringFrom.distanceTo(run.centre) * THROW_OUT));
        } else {
            held = turned(run.hoopaYaw, HoopaFeed.RING_TURN);
            run.bend = bend(run, run.ringFrom, run.centre);
        }
        run.thrown = ringDisplay(run.level, run.ringFrom, held, run.ringSize);
        track(run.thrown, flightPoses(s -> curve(run.ringFrom, run.bend, run.centre, s), run.arrive - run.thrower.throwAt, false, held, portalTurn(run), run.ringSize));
        run.level.playSound(null, run.ringFrom.x, run.ringFrom.y, run.ringFrom.z, SoundEvents.TRIDENT_THROW.value(), SoundSource.NEUTRAL, 1f, 1.3f);
        run.level.playSound(null, run.ringFrom.x, run.ringFrom.y, run.ringFrom.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.6f, 1.6f);
    }

    private static void flyRing(Run run) {
        int age = run.age - run.thrower.throwAt, flight = run.arrive - run.thrower.throwAt;
        Display.ItemDisplay ring = run.thrown;
        if (age >= flight || ring.isRemoved()) {
            remove(ring);
            run.thrown = null;
            return;
        }
        trail(run.level, s -> curve(run.ringFrom, run.bend, run.centre, s), along((double) age / flight, false), along((age + 0.5) / flight, false));
    }

    private static void trail(ServerLevel level, DoubleFunction<Vec3> way, double a, double b) {
        for (int half = 0; half < 2; half++) {
            Vec3 here = way.apply(half == 0 ? a : b);
            level.sendParticles(dust(half == 0 ? 0xFFE145 : 0xF9C539, 1.3f), here.x, here.y, here.z, 2, 0.08, 0.08, 0.08, 0);
            level.sendParticles(ParticleTypes.END_ROD, here.x, here.y, here.z, 1, 0.05, 0.05, 0.05, 0.005);
        }
    }

    private static double along(double t, boolean homing) {
        return homing ? Math.pow(t, EASE) : 1 - Math.pow(1 - t, EASE);
    }

    /**
     * Spins the ring flat like a thrown hoop. The spin dies off with the speed and ends on a whole quarter turn,
     * since the ring is square.
     */
    private static float[] flightPoses(DoubleFunction<Vec3> way, int ticks, boolean homing, Quaternionfc from, Quaternionfc to, float size) {
        float[] poses = new float[ticks * POSE];
        Vec3 start = way.apply(0);
        double turns = Math.toRadians(Math.max(1, Math.round(ticks * FLY_SPIN / 90)) * 90);
        for (int i = 1; i <= ticks; i++) {
            double t = (double) i / ticks, s = along(t, homing);
            Quaternionf turn = new Quaternionf(from).slerp(to, (float) (t * t * (3 - 2 * t))).rotateZ((float) (turns * (homing ? -s : s)));
            pose(poses, i - 1, way.apply(s).subtract(start), turn, size);
        }
        return poses;
    }

    private static void pose(float[] poses, int tick, Vec3 at, Quaternionfc turn, float size) {
        int i = tick * POSE;
        poses[i] = (float) at.x;
        poses[i + 1] = (float) at.y;
        poses[i + 2] = (float) at.z;
        poses[i + 3] = turn.x();
        poses[i + 4] = turn.y();
        poses[i + 5] = turn.z();
        poses[i + 6] = turn.w();
        poses[i + 7] = size;
    }

    private static Transformation pose(Quaternionfc turn, float size) {
        return new Transformation(new Vector3f(), new Quaternionf(turn), new Vector3f(size, size, size), new Quaternionf());
    }

    private static Quaternionf turned(float yaw, Quaternionfc turn) {
        return new Quaternionf().rotationY((float) Math.toRadians(-yaw)).mul(turn);
    }

    private static Quaternionf portalTurn(Run run) {
        return turned(run.facing, run.above ? FLAT : UPRIGHT);
    }

    private static void track(@Nullable Display display, float[] poses) {
        if (display == null) return;
        Track track = new Track(display.getId(), poses);
        for (ServerPlayer player : ((ServerLevel) display.level()).players()) {
            if (player.distanceToSqr(display) < 160 * 160) ServerPlayNetworking.send(player, track);
        }
    }

    private static Vec3 bend(Run run, Vec3 hand, Vec3 far) {
        Vec3 side = new Vec3(hand.x - run.hoopaSpot.x, 0, hand.z - run.hoopaSpot.z);
        Vec3 out = Vec3.directionFromRotation(0, run.hoopaYaw).add(side.lengthSqr() < 1.0E-4 ? Vec3.ZERO : side.normalize()).normalize();
        double blocks = hand.distanceTo(far);
        return hand.add(out.scale(blocks * THROW_OUT)).add(0, blocks * THROW_LIFT, 0);
    }

    private static Vec3 curve(Vec3 from, Vec3 bend, Vec3 to, double s) {
        double r = 1 - s;
        return from.scale(r * r).add(bend.scale(2 * r * s)).add(to.scale(s * s));
    }

    // flies a ring off the wrist, or home to it, along the forearm; for rings that aren't part of a summon's run
    static void fly(ServerLevel level, Vec3 from, Vec3 to, float yaw, Wrist wrist, float size, int ticks, boolean homing, @Nullable Runnable done) {
        Quaternionf held = turnOf(yaw, wrist), lying = turned(yaw, FLAT);
        Display.ItemDisplay ring = ringDisplay(level, from, homing ? lying : held, size);
        if (ring == null) {
            if (done != null) done.run();
            return;
        }
        Vec3 bend = (homing ? to : from).add(handWay(yaw, wrist.fore()).scale(from.distanceTo(to) * THROW_OUT));
        track(ring, flightPoses(s -> curve(from, bend, to, s), ticks, homing, homing ? lying : held, homing ? held : lying, size));
        flights.add(new Flight(level, ring, from, to, bend, ticks, homing, done, new int[]{-1}));
        level.playSound(null, from.x, from.y, from.z, homing ? SoundEvents.TRIDENT_RETURN : SoundEvents.TRIDENT_THROW.value(), SoundSource.NEUTRAL, 1f, 1.3f);
    }

    private static boolean flying(Flight f) {
        int age = ++f.age[0];
        if (age >= f.ticks || f.ring.isRemoved()) {
            remove(f.ring);
            if (f.homing) f.level.sendParticles(dust(0xFFE145, 1.1f), f.to.x, f.to.y, f.to.z, 10, 0.12, 0.12, 0.12, 0);
            if (f.done != null) f.done.run();
            return true;
        }
        trail(f.level, s -> curve(f.from, f.bend, f.to, s), along((double) age / f.ticks, f.homing), along((age + 0.5) / f.ticks, f.homing));
        return false;
    }

    // opening is fast, overshoots and settles; closing swells a touch, then snaps shut
    static void resize(@Nullable Display display, float from, float to, int ticks, boolean opening, Quaternionfc turn) {
        if (display == null) return;
        float[] poses = new float[ticks * POSE];
        for (int i = 1; i <= ticks; i++) {
            float t = i / (float) ticks, u = t - 1;
            float eased = opening ? 1 + (SPRING + 1) * u * u * u + SPRING * u * u : (SPRING + 1) * t * t * t - SPRING * t * t;
            pose(poses, i - 1, Vec3.ZERO, turn, from + (to - from) * eased);
        }
        track(display, poses);
        resizes.add(new Resize(display, to, ticks, turn, new int[]{-1}));
    }

    private static boolean resizing(Resize r) {
        int age = ++r.age[0];
        if (r.display.isRemoved()) return true;
        if (age < r.ticks) return false;
        HoopaFeed.pose(r.display, pose(r.turn, r.to), 1);
        return true;
    }

    private static void goHome(Run run) {
        Vec3 c = run.centre;
        remove(run.portal);
        run.portal = null;
        run.backAt = run.age;
        if (run.hoopa == null) {
            run.level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.HOOPA_PORTAL_CLOSED)),
                    c.x, c.y, c.z, 30, 0.3, 0.3, 0.3, 0.12);
            run.level.sendParticles(dust(0xF3FF00, 1.2f), c.x, c.y, c.z, 12, 0.35, 0.35, 0.35, 0);
            run.level.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 0.8f, 1.2f);
            run.home = run.age;
            return;
        }
        run.backFrom = c;
        float yaw = homeYaw(run);
        run.backTo = hand(run, run.hoopa.position(), yaw, run.thrower.ringTo);
        run.backTicks = run.land > run.age ? run.land - run.age : backTicks(run, run.backTo);
        Quaternionf lying = portalTurn(run), held;
        if (run.on != null) {
            held = turnOf(yaw, run.on);
            run.bend = run.backTo.add(handWay(yaw, run.on.fore()).scale(c.distanceTo(run.backTo) * THROW_OUT));
        } else {
            held = turned(yaw, HoopaFeed.RING_TURN);
            run.bend = bend(run, run.backTo, c);
        }
        run.thrown = ringDisplay(run.level, c, lying, run.ringSize);
        track(run.thrown, flightPoses(s -> curve(run.backFrom, run.bend, run.backTo, s), run.backTicks, true, lying, held, run.ringSize));
        run.level.playSound(null, c.x, c.y, c.z, SoundEvents.TRIDENT_RETURN, SoundSource.NEUTRAL, 1f, 1.4f);
        if (run.thrower.grab != null && run.thrower.catchAt >= run.backTicks) HoopaFeed.send(run.hoopa, run.thrower.grab, run.hoopaYaw);
    }

    private static float homeYaw(Run run) {
        return run.thrower.hold != null ? run.hoopaYaw : run.hoopa.yBodyRot;
    }

    private static void flyHome(Run run) {
        int age = run.age - run.backAt, flight = run.backTicks;
        if (run.hoopa == null || run.thrown == null || run.thrown.isRemoved()) {
            remove(run.thrown);
            run.thrown = null;
            run.home = run.age;
            return;
        }
        Thrower th = run.thrower;
        if (th.grab != null && th.catchAt < flight && age == flight - th.catchAt) HoopaFeed.send(run.hoopa, th.grab, run.hoopaYaw);
        Vec3 to = run.backTo;
        if (age >= flight) {
            remove(run.thrown);
            run.thrown = null;
            run.home = run.age;
            run.level.sendParticles(dust(0xFFE145, 1.1f), to.x, to.y, to.z, 10, 0.12, 0.12, 0.12, 0);
            run.level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 4, 0.08, 0.08, 0.08, 0.02);
            run.level.playSound(null, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.9f, 1.6f);
            return;
        }
        trail(run.level, s -> curve(run.backFrom, run.bend, to, s), along((double) age / flight, true), along((age + 0.5) / flight, true));
    }

    private static void openRing(Run run) {
        Display.ItemDisplay portal = EntityType.ITEM_DISPLAY.create(run.level);
        if (portal == null) return;
        portal.setPos(run.centre.x, run.centre.y, run.centre.z);
        portal.setYRot(run.facing);
        ((ItemDisplayAccessor) portal).hyperspace_wormholes$setItem(new ItemStack(ModItems.HOOPA_PORTAL_CLOSED));
        ((DisplayAccessor) portal).hyperspace_wormholes$setBrightness(new Brightness(15, 15));
        ((DisplayAccessor) portal).hyperspace_wormholes$setViewRange(4f);
        ((DisplayAccessor) portal).hyperspace_wormholes$setTransformation(pose(run.above ? FLAT : UPRIGHT, run.hoop));
        add(run.level, portal);
        run.portal = portal;
        Vec3 c = run.centre;
        run.level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 14, 0.25, 0.25, 0.25, 0.1);
        run.level.sendParticles(dust(0xF3FF00, 1.2f), c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0);
        run.level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1f, 1.4f);
        run.level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.7f, 1.8f);
    }

    private static void fill(Run run) {
        item(run.portal, ModItems.HOOPA_PORTAL);
        Vec3 c = run.centre;
        double r = run.scale * INSIDE / 2;
        run.level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 24, r * 0.5, r * 0.5, r * 0.5, 0.06);
        run.level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 40, r * 0.4, r * 0.4, r * 0.4, 0.2);
        run.level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 1f, 1.2f);
    }

    private static void rimSparkle(Run run, int count) {
        if (run.portal == null) return;
        Vec3 side = run.above ? new Vec3(1, 0, 0) : new Vec3(-run.normal.z, 0, run.normal.x);
        Vec3 up = run.above ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        double r = run.scale * RIM;
        for (int i = 0; i < count; i++) {
            // a random spot along the square ring's bars
            double along = (run.level.getRandom().nextDouble() * 2 - 1) * r;
            boolean flat = run.level.getRandom().nextBoolean();
            double sign = run.level.getRandom().nextBoolean() ? r : -r;
            Vec3 at = run.centre.add(side.scale(flat ? along : sign)).add(up.scale(flat ? sign : along)).add(run.normal.scale(0.1));
            run.level.sendParticles(i % 2 == 0 ? ParticleTypes.WAX_ON : ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.01);
        }
    }

    private static void bringOut(Run run) {
        PokemonEntity p = run.summoned;
        p.moveTo(run.in.x, run.in.y, run.in.z, run.facing, 0f);
        run.noGravity = p.isNoGravity();
        p.setNoGravity(true);
        p.setNoAi(true);
        if (run.flier) p.setFlying(true);
        run.level.addFreshEntity(p);
        clip(run, true);
        run.level.playSound(null, run.centre.x, run.centre.y, run.centre.z, SoundEvents.PORTAL_TRAVEL, SoundSource.NEUTRAL, 0.25f, 1.8f);
    }

    private static void carry(Run run, float t) {
        Vec3 at;
        if (run.flier || run.above) {
            float eased = t * t * (3 - 2 * t);
            at = run.in.lerp(run.out, eased);
        } else {
            float eased = 1 - (1 - t) * (1 - t);
            at = run.in.lerp(run.out, eased).add(0, Math.sin(Math.PI * eased) * LEAP * run.scale * INSIDE, 0);
        }
        place(run, at);
    }

    private static void hover(Run run) {
        int t = run.age - run.done;
        double drift = HOVER_DRIFT * (1 - Math.exp(-t / 25.0));
        place(run, run.hovered.add(run.normal.scale(drift)).add(0, Math.sin(t / 9.0) * BOB, 0));
    }

    private static void startLanding(Run run) {
        PokemonEntity p = run.summoned;
        if (p == null || p.isRemoved()) return;
        run.landAt = run.age;
        run.landFrom = p.position();
        Vec3 ground = null;
        for (int down = 0; ground == null && down <= 24; down += 4) ground = ground(run.level, run.landFrom.add(0, -down, 0));
        run.landTo = ground != null ? ground : new Vec3(run.landFrom.x, run.landFrom.y - 2, run.landFrom.z);
    }

    private static void land(Run run, float t) {
        PokemonEntity p = run.summoned;
        if (p == null || p.isRemoved()) return;
        if (t >= 1) {
            place(run, run.landTo);
            p.setFlying(false);
            release(run);
            return;
        }
        float eased = t * t * (3 - 2 * t);
        place(run, run.landFrom.lerp(run.landTo, eased));
    }

    private static void place(Run run, Vec3 at) {
        PokemonEntity p = run.summoned;
        if (p == null || p.isRemoved()) return;
        p.setPos(at);
        p.setDeltaMovement(Vec3.ZERO);
        p.setYRot(run.facing);
        p.setYBodyRot(run.facing);
        p.setYHeadRot(run.facing);
        p.fallDistance = 0;
    }

    private static void release(Run run) {
        PokemonEntity p = run.summoned;
        run.released = true;
        if (p == null || p.isRemoved()) return;
        p.setNoGravity(run.noGravity);
        p.setNoAi(false);
    }

    private static void clip(Run run, boolean on) {
        if (run.clipped == on || run.summoned == null) return;
        run.clipped = on;
        sendClip(run.level, run.summoned, run.centre, run.normal, on);
    }

    // tells nearby clients to hide whatever of the entity is behind the plane through point (normal points to the side that shows), or to stop
    static void sendClip(ServerLevel level, net.minecraft.world.entity.Entity entity, Vec3 point, Vec3 normal, boolean on) {
        Clip clip = new Clip(entity.getId(), point.x, point.y, point.z, (float) normal.x, (float) normal.y, (float) normal.z, on);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(point) < 160 * 160) ServerPlayNetworking.send(player, clip);
        }
    }

    private static void item(Display.ItemDisplay display, net.minecraft.world.item.Item item) {
        if (display != null) ((ItemDisplayAccessor) display).hyperspace_wormholes$setItem(new ItemStack(item));
    }

    static Transformation flat(float scale) {
        return pose(FLAT, scale);
    }

    private static void add(ServerLevel level, Display display) {
        display.addTag(TAG);
        ours.add(display.getUUID());
        level.addFreshEntity(display);
    }

    private static void remove(Display display) {
        if (display == null) return;
        ours.remove(display.getUUID());
        display.discard();
    }

    private static void finish(Run run) {
        remove(run.thrown);
        remove(run.portal);
        clip(run, false);
        PokemonEntity p = run.summoned;
        if (p != null && !p.isRemoved() && run.age >= run.emerge && !run.released) {
            if (run.flier) p.setFlying(false);
            release(run);
        }
        if (run.hoopa != null && !run.hoopa.isRemoved() && !run.keep && (run.age < run.thrower.clipEnd || (run.thrower.hold != null && run.home < 0))) {
            HoopaFeed.send(run.hoopa, "", run.hoopaYaw);
        }
    }
}
