package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.mojang.math.Transformation;
import dev.hyperspacewormholes.hyperspace.mixin.DisplayAccessor;
import dev.hyperspacewormholes.hyperspace.mixin.ItemDisplayAccessor;
import io.netty.buffer.ByteBuf;
import kotlin.Unit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hoopa's part when a donut is fed at a spatial distortion, after the Legends Z-A DLC's gate cutscene: it eats the
 * donut bite by bite while a baked "feed" clip plays as a Cobblemon primary animation on every watching client (the
 * Play payload, see HoopaFeedClient), then throws a real gold ring (an item display) that opens the distortion where it
 * lands. A party Hoopa pops out of its ball first.
 * Its look-at target is cleared and its yaw, body yaw and head yaw are pinned every tick, here and on the clients,
 * since a client only turns a standing mob's body once its head is 50-75 degrees off and the primary animation stops
 * the head bone from making up the rest. The donut is given back if the portal can't open or Hoopa or the player is
 * gone first.
 */
public class HoopaFeed {
    public static final String GROUP = "hoopa_confined_hyperspace", MOTION = "feed";
    private static final String TAG = "hyperspace_wormholes_hoopa_donut";
    private static final int[] BITES = {15, 31, 38, 60};
    private static final int CRUMBS_FROM = 14, CRUMBS_TO = 62, CRUMBS_NEAR = 4, BURP = 68;
    private static final int TURN = 72, TURN_TICKS = 8, RIM = 84, RIM_END = 107, GATE = 103, FLASH = 138;
    private static final int THROW = 163, END = 180, GO_BACK = 196;
    static final double[] RING_FROM = {13.9, 7.78, -0.24};
    static final Quaternionfc RING_TURN = new Quaternionf(-0.3328f, -0.4379f, -0.7883f, 0.2758f).normalize();
    static final float RING_PX = 5, RING_FLARE_SIZE = 4;
    static final double RING_SPEED = 0.55, RING_ARC = 0.7, RING_ARC_PER_BLOCK = 0.05, RING_ARC_MAX = 2.5, RIFT_HEIGHT = 1.5;
    private static final int RING_MIN_TICKS = 5, RING_MAX_TICKS = 30, RING_STEP = 2, RING_FLARE = 3;
    static final float RING_SPIN = 40f, FACE_STEP = 0.5f;
    private static final int SEND_OUT_WAIT = 60;
    private static final double SEND_OUT_REACH = 12, SEND_OUT_DROP = 5, CLEAR_OF_HOLE = 2.5, SIDE = 1.1, AHEAD = 2.5;
    private static final float DONUT_SIZE = 0.26f;

    private static final Map<UUID, Run> runs = new HashMap<>();
    private static final Set<UUID> ours = new HashSet<>();

    public record Play(int entity, String motion, float yaw) implements CustomPacketPayload {
        public static final String FACE = "face";
        public static final Type<Play> TYPE = new Type<>(Hyperspace.id("hoopa_feed"));
        public static final StreamCodec<ByteBuf, Play> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Play::entity,
                ByteBufCodecs.STRING_UTF8, Play::motion, ByteBufCodecs.FLOAT, Play::yaw, Play::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static class Run {
        ServerPlayer player;
        Distortions.Distortion distortion;
        ItemStack donut;
        PokemonEntity hoopa;
        Pokemon sent;
        Vec3 spot;
        boolean ready, opened;
        int waited, age = -1, openAt = Integer.MAX_VALUE;
        float yaw, sentYaw, ringSize;
        Display.ItemDisplay held, ring;
        Vec3 ringFrom, ringTo;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Play.TYPE, Play.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(HoopaFeed::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.getTags().contains(TAG) && !ours.contains(entity.getUUID())) entity.discard();
        });
    }

    public static boolean busy(ServerPlayer player, Distortions.Distortion distortion, PokemonEntity hoopa) {
        if (runs.containsKey(player.getUUID())) return true;
        for (Run run : runs.values()) {
            if (run.distortion == distortion || (hoopa != null && run.hoopa == hoopa)) return true;
        }
        return false;
    }

    public static void start(ServerPlayer player, Distortions.Distortion distortion, ItemStack donut, PokemonEntity out, Pokemon party) {
        Run run = new Run();
        run.player = player;
        run.distortion = distortion;
        run.donut = donut;
        runs.put(player.getUUID(), run);
        if (out != null) {
            run.hoopa = out;
            run.spot = out.position();
            run.ready = true;
            return;
        }
        run.spot = sendOutSpot(player, party, distortion.base());
        if (party.getEntity() != null) party.recall();
        run.sent = party;
        party.sendOutWithAnimation(player, player.serverLevel(), run.spot, null, true, null, entity -> {
            run.hoopa = entity;
            return Unit.INSTANCE;
        }).thenAccept(entity -> {
            run.hoopa = entity;
            run.ready = true;
        });
    }

    private static Vec3 sendOutSpot(ServerPlayer player, Pokemon party, Vec3 hole) {
        Vec3 aimed = PlayerExtensionsKt.raycastSafeSendout(player, party, SEND_OUT_REACH, SEND_OUT_DROP, ClipContext.Fluid.ANY);
        if (aimed != null && Math.hypot(aimed.x - hole.x, aimed.z - hole.z) >= CLEAR_OF_HOLE) return aimed;
        return beside(player, hole);
    }

    private static Vec3 beside(ServerPlayer player, Vec3 target) {
        Vec3 to = new Vec3(target.x - player.getX(), 0, target.z - player.getZ());
        double ahead = Mth.clamp(to.length() * 0.45, 0.5, AHEAD);
        to = to.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, player.getYRot()) : to.normalize();
        Vec3 side = new Vec3(-to.z, 0, to.x);
        for (double s : new double[] {SIDE, -SIDE, SIDE / 2, -SIDE / 2}) {
            Vec3 ground = ground(player.level(), player.position().add(side.scale(s)).add(to.scale(ahead)));
            if (ground != null) return ground;
        }
        return player.position();
    }

    private static Vec3 ground(Level level, Vec3 at) {
        BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy >= -3; dy--) {
            block.set(Mth.floor(at.x), Mth.floor(at.y) + dy - 1, Mth.floor(at.z));
            var shape = level.getBlockState(block).getCollisionShape(level, block);
            if (shape.isEmpty()) continue;
            Vec3 top = new Vec3(at.x, block.getY() + shape.max(net.minecraft.core.Direction.Axis.Y), at.z);
            return level.noCollision(new AABB(top.x - 0.4, top.y + 0.01, top.z - 0.4, top.x + 0.4, top.y + 1.2, top.z + 0.4)) ? top : null;
        }
        return null;
    }

    private static void tick(MinecraftServer server) {
        Iterator<Run> it = runs.values().iterator();
        while (it.hasNext()) {
            Run run = it.next();
            if (!step(run)) {
                finish(run);
                it.remove();
            }
        }
    }

    private static boolean step(Run run) {
        ServerPlayer player = run.player;
        boolean playerGone = player.isRemoved() || player.hasDisconnected();
        if (run.hoopa == null || !run.ready) {
            if (playerGone || ++run.waited > SEND_OUT_WAIT) return giveBack(run);
            if (run.hoopa != null && run.hoopa.isRemoved()) return giveBack(run);
            return true;
        }
        PokemonEntity hoopa = run.hoopa;
        if (hoopa.isRemoved() || playerGone || hoopa.level() != player.level()) {
            if (!run.opened) giveBack(run);
            return false;
        }
        ServerLevel level = (ServerLevel) hoopa.level();
        run.age++;
        int age = run.age;
        if (age == 0) {
            if (run.sent != null) run.spot = hoopa.position();
            run.yaw = yawTo(run.spot, player.position());
            hold(run);
            send(hoopa, MOTION, run.yaw);
            run.sentYaw = run.yaw;
            run.held = donut(level, run);
        }
        if (age < TURN) {
            run.yaw = Mth.approachDegrees(run.yaw, yawTo(run.spot, player.position()), 6);
        } else if (age < TURN + TURN_TICKS) {
            run.yaw = Mth.approachDegrees(run.yaw, yawTo(run.spot, run.distortion.base()), 180f / TURN_TICKS);
        } else {
            run.yaw = yawTo(run.spot, run.distortion.base());
        }
        hold(run);
        if (Math.abs(Mth.wrapDegrees(run.yaw - run.sentYaw)) > FACE_STEP) {
            send(hoopa, Play.FACE, run.yaw);
            run.sentYaw = run.yaw;
        }
        Vec3 mouth = mouth(hoopa, run.yaw);
        if (run.held != null) {
            run.held.setPos(mouth);
            for (int i = 0; i < BITES.length; i++) {
                if (age != BITES[i]) continue;
                level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8f, 1.1f + 0.1f * i);
                if (i == BITES.length - 1) {
                    ours.remove(run.held.getUUID());
                    run.held.discard();
                    run.held = null;
                } else {
                    size(run.held, DONUT_SIZE * (BITES.length - 1 - i) / (BITES.length - 1), 3);
                }
            }
        }
        if (age >= CRUMBS_FROM && age <= CRUMBS_TO && age % 3 == 0 && nearBite(age)) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, run.donut),
                    mouth.x, mouth.y, mouth.z, 1, 0.04, 0.03, 0.04, 0.02);
        }
        if (age == BURP) level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.PLAYER_BURP, SoundSource.NEUTRAL, 0.5f, 1.6f);
        if (age == RIM) {
            Distortions.stir(level, run.distortion);
            Vec3 middle = hoopa.position().add(0, hoopa.getBbHeight() / 2, 0);
            level.sendParticles(HoopaSummon.dust(0xFF47CA, 1f), middle.x, middle.y, middle.z, 16,
                    hoopa.getBbWidth() * 0.6, hoopa.getBbHeight() * 0.5, hoopa.getBbWidth() * 0.6, 0);
            level.sendParticles(ParticleTypes.END_ROD, middle.x, middle.y, middle.z, 3, 0.3, 0.4, 0.3, 0.04);
            level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 0.8f, 1.5f);
        }
        if (age > RIM && age < RIM_END && age % 3 == 0) {
            Vec3 middle = hoopa.position().add(0, hoopa.getBbHeight() / 2, 0);
            level.sendParticles(HoopaSummon.dust(age % 6 == 0 ? 0xFFD5FF : 0xFF47CA, 1f), middle.x, middle.y, middle.z, 6,
                    hoopa.getBbWidth() * 0.5, hoopa.getBbHeight() * 0.45, hoopa.getBbWidth() * 0.5, 0);
        }
        if (age == GATE || age == FLASH) {
            Vec3 head = hoopa.position().add(0, hoopa.getBbHeight() * 0.85, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, head.x, head.y, head.z, 6, 0.35, 0.2, 0.35, 0.05);
            level.playSound(null, head.x, head.y, head.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.NEUTRAL, 0.7f, age == FLASH ? 1.6f : 1.2f);
        }
        if (age == THROW) throwRing(level, run);
        if (run.ring != null) flyRing(level, run);
        if (age == run.openAt) {
            run.opened = true;
            if (!WorldData.get(level.getServer()).distortions.contains(run.distortion)) {
                giveBack(run);
                if (!playerGone) Distortions.noneNearby(player);
            } else if (!Distortions.open(player, run.distortion, run.donut)) {
                giveBack(run);
            }
        }
        if (age >= END && run.sent == null && run.ring == null) return false;
        if (age >= GO_BACK) {
            if (run.sent != null && run.sent.getEntity() == hoopa) run.sent.tryRecallWithAnimation();
            return false;
        }
        return true;
    }

    private static void throwRing(ServerLevel level, Run run) {
        PokemonEntity hoopa = run.hoopa;
        double scale = hoopa.getPokemon().getForm().getBaseScale() * hoopa.getPokemon().getEffectiveScale() / 16;
        Vec3 ahead = Vec3.directionFromRotation(0, run.yaw);
        Vec3 left = new Vec3(ahead.z, 0, -ahead.x);
        run.ringSize = (float) (RING_PX * scale);
        run.ringFrom = hoopa.position().add(left.scale(RING_FROM[0] * scale)).add(0, RING_FROM[1] * scale, 0)
                .add(ahead.scale(RING_FROM[2] * scale));
        run.ringTo = run.distortion.base().add(run.distortion.normal().scale(0.1)).add(0, RIFT_HEIGHT, 0);
        int flight = Mth.clamp((int) Math.round(run.ringFrom.distanceTo(run.ringTo) / RING_SPEED), RING_MIN_TICKS, RING_MAX_TICKS);
        run.openAt = THROW + flight;
        Display.ItemDisplay ring = EntityType.ITEM_DISPLAY.create(level);
        if (ring != null) {
            ring.setPos(run.ringFrom.x, run.ringFrom.y, run.ringFrom.z);
            ((ItemDisplayAccessor) ring).hyperspace_wormholes$setItem(new ItemStack(ModItems.HOOPA_THROWN_RING));
            ((DisplayAccessor) ring).hyperspace_wormholes$setBrightness(new Brightness(15, 15));
            ((DisplayAccessor) ring).hyperspace_wormholes$setTransformation(ringPose(Vec3.ZERO, run.yaw, RING_TURN, run.ringSize));
            ring.addTag(TAG);
            ours.add(ring.getUUID());
            level.addFreshEntity(ring);
            run.ring = ring;
        }
        level.playSound(null, run.ringFrom.x, run.ringFrom.y, run.ringFrom.z, SoundEvents.TRIDENT_THROW.value(), SoundSource.NEUTRAL, 1f, 1.3f);
        level.playSound(null, run.ringFrom.x, run.ringFrom.y, run.ringFrom.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.6f, 1.6f);
    }

    private static void flyRing(ServerLevel level, Run run) {
        int age = run.age - THROW, flight = run.openAt - THROW;
        Display.ItemDisplay ring = run.ring;
        if (age >= flight + RING_FLARE || ring.isRemoved()) {
            ours.remove(ring.getUUID());
            ring.discard();
            run.ring = null;
            return;
        }
        float wall = yawTo(Vec3.ZERO, run.distortion.normal());
        if (age >= flight) {
            if (age == flight) {
                pose(ring, ringPose(run.ringTo.subtract(run.ringFrom), wall, new Quaternionf(), run.ringSize * RING_FLARE_SIZE), RING_FLARE);
                level.sendParticles(ParticleTypes.END_ROD, run.ringTo.x, run.ringTo.y, run.ringTo.z, 16, 0.3, 0.3, 0.3, 0.12);
                level.sendParticles(HoopaSummon.dust(0xF3FF00, 1.2f), run.ringTo.x, run.ringTo.y, run.ringTo.z, 14, 0.4, 0.4, 0.4, 0);
            }
            return;
        }
        for (int half = 0; half < 2; half++) {
            Vec3 here = ringAt(run, (age + half * 0.5) / flight);
            level.sendParticles(HoopaSummon.dust(half == 0 ? 0xFFE145 : 0xF9C539, 1.3f), here.x, here.y, here.z, 2, 0.08, 0.08, 0.08, 0);
            level.sendParticles(ParticleTypes.END_ROD, here.x, here.y, here.z, 1, 0.05, 0.05, 0.05, 0.005);
            if (age % 2 == half) level.sendParticles(ParticleTypes.WAX_ON, here.x, here.y, here.z, 1, 0.1, 0.1, 0.1, 0);
        }
        if (age % RING_STEP != 0) return;
        int next = Math.min(age + RING_STEP, flight);
        Vec3 at = ringAt(run, (double) next / flight).subtract(run.ringFrom);
        pose(ring, next >= flight ? ringPose(at, wall, new Quaternionf(), run.ringSize)
                : ringPose(at, run.yaw + RING_SPIN * next, RING_TURN, run.ringSize), next - age);
    }

    private static Vec3 ringAt(Run run, double s) {
        double arc = Math.min(RING_ARC + RING_ARC_PER_BLOCK * run.ringFrom.distanceTo(run.ringTo), RING_ARC_MAX);
        return run.ringFrom.lerp(run.ringTo, s).add(0, arc * 4 * s * (1 - s), 0);
    }

    static Transformation ringPose(Vec3 at, float yaw, Quaternionfc held, float scale) {
        Quaternionf turn = new Quaternionf().rotationY((float) Math.toRadians(-yaw)).mul(held);
        return new Transformation(new Vector3f((float) at.x, (float) at.y, (float) at.z), turn, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    static void pose(Display display, Transformation pose, int ticks) {
        ((DisplayAccessor) display).hyperspace_wormholes$setDuration(ticks);
        ((DisplayAccessor) display).hyperspace_wormholes$setDelay(0);
        ((DisplayAccessor) display).hyperspace_wormholes$setTransformation(pose);
    }

    private static void hold(Run run) {
        PokemonEntity hoopa = run.hoopa;
        hoopa.getNavigation().stop();
        hoopa.setDeltaMovement(Vec3.ZERO);
        hoopa.setPos(run.spot);
        hoopa.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        hoopa.setYRot(run.yaw);
        hoopa.setYBodyRot(run.yaw);
        hoopa.setYHeadRot(run.yaw);
        hoopa.yRotO = run.yaw;
        hoopa.yBodyRotO = run.yaw;
        hoopa.yHeadRotO = run.yaw;
        hoopa.setXRot(0);
    }

    static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    private static Vec3 mouth(PokemonEntity hoopa, float yaw) {
        Vec3 ahead = Vec3.directionFromRotation(0, yaw).scale(hoopa.getBbWidth() * 0.5 + 0.15);
        return hoopa.position().add(ahead).add(0, hoopa.getBbHeight() * 0.8, 0);
    }

    private static Display.ItemDisplay donut(ServerLevel level, Run run) {
        Display.ItemDisplay held = EntityType.ITEM_DISPLAY.create(level);
        if (held == null) return null;
        Vec3 at = mouth(run.hoopa, run.yaw);
        held.setPos(at.x, at.y, at.z);
        ((ItemDisplayAccessor) held).hyperspace_wormholes$setItem(run.donut.copyWithCount(1));
        ((DisplayAccessor) held).hyperspace_wormholes$setBillboard(Display.BillboardConstraints.CENTER);
        ((DisplayAccessor) held).hyperspace_wormholes$setBrightness(new Brightness(15, 15));
        size(held, DONUT_SIZE, 0);
        held.addTag(TAG);
        ours.add(held.getUUID());
        level.addFreshEntity(held);
        return held;
    }

    private static void size(Display display, float scale, int ticks) {
        ((DisplayAccessor) display).hyperspace_wormholes$setDuration(ticks);
        ((DisplayAccessor) display).hyperspace_wormholes$setDelay(0);
        ((DisplayAccessor) display).hyperspace_wormholes$setTransformation(
                new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
    }

    static void send(PokemonEntity hoopa, String motion, float yaw) {
        Set<ServerPlayer> to = new HashSet<>(PlayerLookup.tracking(hoopa));
        if (hoopa.level() instanceof ServerLevel level) {
            for (ServerPlayer player : level.players()) if (player.distanceToSqr(hoopa) < 64 * 64) to.add(player);
        }
        Play play = new Play(hoopa.getId(), motion, yaw);
        for (ServerPlayer player : to) ServerPlayNetworking.send(player, play);
    }

    private static boolean nearBite(int age) {
        for (int bite : BITES) if (Math.abs(age - bite) <= CRUMBS_NEAR) return true;
        return false;
    }

    private static boolean giveBack(Run run) {
        if (run.donut.isEmpty()) return false;
        ItemStack back = run.donut.copy();
        run.donut = ItemStack.EMPTY;
        ServerPlayer player = run.player;
        if (!player.isRemoved() && !player.hasDisconnected()) {
            if (!player.getInventory().add(back)) player.drop(back, false);
        } else if (run.hoopa != null && run.hoopa.level() instanceof ServerLevel level) {
            level.addFreshEntity(new ItemEntity(level, run.spot.x, run.spot.y + 0.5, run.spot.z, back));
        }
        return false;
    }

    private static void finish(Run run) {
        if (run.held != null) {
            ours.remove(run.held.getUUID());
            run.held.discard();
        }
        if (run.ring != null) {
            ours.remove(run.ring.getUUID());
            run.ring.discard();
        }
        if (run.hoopa != null) {
            if (!run.hoopa.isRemoved()) {
                if (run.age >= 0 && run.age < END) send(run.hoopa, "", run.yaw);
                if (run.sent != null && run.sent.getEntity() == run.hoopa && run.age < GO_BACK) run.sent.tryRecallWithAnimation();
            }
        }
    }
}
