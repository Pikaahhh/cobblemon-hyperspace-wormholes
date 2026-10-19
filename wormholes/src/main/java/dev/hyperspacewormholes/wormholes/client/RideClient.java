package dev.hyperspacewormholes.wormholes.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.mixin.SoundEngineAccessor;
import dev.hyperspacewormholes.wormholes.mixin.SoundManagerAccessor;
import dev.hyperspacewormholes.wormholes.network.RideFollowPayload;
import dev.hyperspacewormholes.wormholes.network.RideStartPayload;
import dev.hyperspacewormholes.wormholes.network.RideStatePayload;
import dev.hyperspacewormholes.wormholes.ride.RideLegend;
import dev.hyperspacewormholes.wormholes.ride.Stamina;
import dev.hyperspacewormholes.wormholes.ride.TunnelLayout;
import dev.hyperspacewormholes.wormholes.ride.WarpTunnel;
import dev.hyperspacewormholes.wormholes.ride.WormholeType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Client side of the Ultra Warp Ride. Simulates flying forward and steering, spots when you fly through a wormhole or
 * golden ring, handles stamina, and tells the server where you're at every tick.
 */
public final class RideClient {
    public static final int TRAIL_LENGTH = 26;
    private static final float STEER_ACCEL = 0.17f;
    private static final float STEER_DRAG = 0.85f;
    private static final float STREAK_SPAWN_Z = 420f;
    private static final SoundEvent STAT_UP = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath("cobblemon", "status.up.actor"));

    private static boolean active;
    private static boolean passenger;
    private static double followProgress;
    private static float followX, followY;
    private static long seed;
    private static TunnelLayout layout = TunnelLayout.seeded(0);
    private static RideLegend mount = RideLegend.NONE;
    private static int ticks;
    private static double progress, prevProgress;
    private static double warp, prevWarp;
    private static float offX, offY, prevOffX, prevOffY, velX, velY;
    private static float speed;
    private static float facingYaw, facingPitch;
    private static float roll, prevRoll;
    private static int nextWormhole;
    private static int nextGoldRing;
    private static Stamina stamina = new Stamina();
    private static int dragTarget = -1;
    private static int boostUntil;
    private static CameraType previousCamera;
    private static float flash, prevFlash;
    private static float whiteOutRate;
    private static boolean muted;
    private static final Map<Integer, Integer> COLLECTED_RINGS = new HashMap<>();
    private static final Map<Integer, Integer> TOUCHED = new HashMap<>();
    private static final List<Streak> STREAKS = new ArrayList<>();
    private static final float[] TRAIL_X = new float[TRAIL_LENGTH];
    private static final float[] TRAIL_Y = new float[TRAIL_LENGTH];
    private static final double[] TRAIL_PROGRESS = new double[TRAIL_LENGTH];

    private RideClient() {}

    public static final class Streak {
        public float angle, radius, z, prevZ, length, width, speedFactor, brightness;
    }

    public static boolean isActive() {
        return active;
    }

    public static TunnelLayout layout() {
        return layout;
    }

    public static long seed() {
        return seed;
    }

    public static RideLegend mount() {
        return mount;
    }

    public static float time(float partialTick) {
        return ticks + partialTick;
    }

    public static double progress(float partialTick) {
        return Mth.lerp(partialTick, prevProgress, progress);
    }

    public static double warpDistance(float partialTick) {
        return Mth.lerp(partialTick, prevWarp, warp);
    }

    public static float offX(float partialTick) {
        return Mth.lerp(partialTick, prevOffX, offX);
    }

    public static float offY(float partialTick) {
        return Mth.lerp(partialTick, prevOffY, offY);
    }

    public static float roll(float partialTick) {
        return Mth.lerp(partialTick, prevRoll, roll);
    }

    public static float facingYaw() {
        return facingYaw;
    }

    public static float speed() {
        return speed;
    }

    public static float flash(float partialTick) {
        return Mth.lerp(partialTick, prevFlash, flash);
    }

    public static int staminaHalves() {
        return stamina.halves();
    }

    public static boolean exhausted() {
        return dragTarget >= 0;
    }

    public static int collectedAge(int index) {
        Integer at = COLLECTED_RINGS.get(index);
        return at == null ? -1 : ticks - at;
    }

    public static int touchedAge(int index) {
        Integer at = TOUCHED.get(index);
        return at == null ? -1 : ticks - at;
    }

    public static float trailX(int i) {
        return TRAIL_X[i];
    }

    public static float trailY(int i) {
        return TRAIL_Y[i];
    }

    public static double trailProgress(int i) {
        return TRAIL_PROGRESS[i];
    }

    public static List<Streak> streaks() {
        return STREAKS;
    }

    public static void start(RideStartPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        List<RideStartPayload.WaystoneHole> holes = payload.waystones();
        active = true;
        seed = payload.seed();
        layout = holes.isEmpty() ? TunnelLayout.seeded(seed, payload.override()) : TunnelLayout.fixed(seed,
                IntStream.range(0, holes.size()).mapToObj(i -> new WarpTunnel.Wormhole(i, holes.get(i).distance(),
                        holes.get(i).x(), holes.get(i).y(), 0, WormholeType.byId(holes.get(i).type()))).toList(),
                holes.stream().map(RideStartPayload.WaystoneHole::name).toList());
        mount = RideLegend.byId(payload.mount());
        passenger = payload.passenger();
        followProgress = followX = followY = 0;
        ticks = 0;
        progress = prevProgress = warp = prevWarp = 0;
        offX = offY = prevOffX = prevOffY = velX = velY = 0;
        speed = WarpTunnel.CRUISE_SPEED;
        facingYaw = facingPitch = roll = prevRoll = 0;
        nextWormhole = nextGoldRing = 0;
        TOUCHED.clear();
        stamina = new Stamina();
        dragTarget = -1;
        COLLECTED_RINGS.clear();
        boostUntil = 0;
        seedStreaks(RandomSource.create(seed));
        Arrays.fill(TRAIL_X, 0f);
        Arrays.fill(TRAIL_Y, 0f);
        for (int i = 0; i < TRAIL_LENGTH; i++) TRAIL_PROGRESS[i] = -i * WarpTunnel.CRUISE_SPEED;
        flash = prevFlash = 1f;
        whiteOutRate = 0f;

        previousCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        RideFlightSounds.forMount(mount).forEach(mc.getSoundManager()::play);
        JadeCompat.hide();
        play(SoundEvents.BEACON_ACTIVATE, 1.8f, 1f);
    }

    public static void end() {
        if (active || whiteOutRate > 0) {
            flash = prevFlash = 1f;
            whiteOutRate = 0f;
        }
        if (!active) return;
        active = false;
        Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        previousCamera = null;
        JadeCompat.restore();
        UltraWormholeMusic.fadeOut();
        play(SoundEvents.BEACON_DEACTIVATE, 1.4f, 0.9f);
    }

    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        active = false;
        flash = prevFlash = 0f;
        muteWithFlash(mc);
        whiteOutRate = 0f;
        if (previousCamera != null) mc.options.setCameraType(previousCamera);
        previousCamera = null;
        JadeCompat.restore();
        UltraWormholeMusic.stopNow();
    }

    public static void whiteOut(int ticks) {
        whiteOutRate = 1f / Math.max(1, ticks);
        play(SoundEvents.BEACON_POWER_SELECT, 0.7f, 1f);
    }

    public static void onFollow(RideFollowPayload payload) {
        if (!active || !passenger) return;
        followProgress = Math.max(followProgress, payload.progress());
        followX = payload.offX();
        followY = payload.offY();
    }

    public static void tick(Minecraft mc) {
        prevFlash = flash;
        if (flash > 0 && mc.screen instanceof ReceivingLevelScreen) {
            flash = prevFlash = 1f;
        } else {
            flash = whiteOutRate > 0 ? Math.min(1f, flash + whiteOutRate) : Math.max(0f, flash - 0.05f);
        }
        muteWithFlash(mc);
        if (!active) return;

        LocalPlayer player = mc.player;
        if (player == null) {
            reset();
            return;
        }
        if (player.level().dimension() != Wormholes.ULTRA_WARP) return;

        ticks++;
        if (ticks % 40 == 0 && !UltraWormholeMusic.isPlayingForRide()) UltraWormholeMusic.start();
        stamina.tick(ticks);
        if (passenger) {
            follow();
        } else {
            if (stamina.exhausted() && dragTarget < 0) {
                dragTarget = layout.firstWormholeAfter(progress);
                play(SoundEvents.BEACON_DEACTIVATE, 0.7f, 1f);
            }
            steer(player);
            advance();
        }
        recordTrail();
        keepSeated(player);
        if (ticks % 10 == 0) clearGhosts(mc, player);
        aim(mc, player);
        tickStreaks();
        spawnTrail(player);

        if (!passenger) ClientPlayNetworking.send(new RideStatePayload(progress, offX, offY));
    }

    private static void muteWithFlash(Minecraft mc) {
        if (flash <= 0 && !muted) return;
        var engine = (SoundEngineAccessor) ((SoundManagerAccessor) mc.getSoundManager()).hyperspace_wormholes$getSoundEngine();
        if (!engine.hyperspace_wormholes$isLoaded()) return;
        float master = mc.options.getSoundSourceVolume(SoundSource.MASTER);
        engine.hyperspace_wormholes$getListener().setGain(master * (1 - flash));
        muted = flash > 0;
    }

    private static void recordTrail() {
        System.arraycopy(TRAIL_X, 0, TRAIL_X, 1, TRAIL_LENGTH - 1);
        System.arraycopy(TRAIL_Y, 0, TRAIL_Y, 1, TRAIL_LENGTH - 1);
        System.arraycopy(TRAIL_PROGRESS, 0, TRAIL_PROGRESS, 1, TRAIL_LENGTH - 1);
        TRAIL_X[0] = offX;
        TRAIL_Y[0] = offY;
        TRAIL_PROGRESS[0] = progress;
    }

    private static void steer(LocalPlayer player) {
        if (dragTarget >= 0) {
            dragged();
            return;
        }
        velX = (velX + player.input.leftImpulse * STEER_ACCEL) * STEER_DRAG;
        velY = (velY + player.input.forwardImpulse * STEER_ACCEL) * STEER_DRAG;
        move();
        float len = Mth.sqrt(offX * offX + offY * offY);
        if (len > WarpTunnel.MAX_OFFSET) {
            float nx = offX / len, ny = offY / len;
            offX = nx * WarpTunnel.MAX_OFFSET;
            offY = ny * WarpTunnel.MAX_OFFSET;
            float outward = velX * nx + velY * ny;
            if (outward > 0) {
                velX -= outward * nx;
                velY -= outward * ny;
            }
        }
        bank();
        boolean boosted = ticks < boostUntil;
        float cruise = WarpTunnel.CRUISE_SPEED * stamina.speedFactor();
        speed = Mth.lerp(boosted ? 0.2f : 0.06f, speed, cruise * (boosted ? 2f : 1f));
    }

    private static void dragged() {
        WarpTunnel.Wormhole target = layout.wormhole(dragTarget);
        float remaining = (float) Math.max(1, target.distance() - progress);
        float pull = Mth.clamp(speed * 2f / remaining, 0.04f, 1f);
        velX = (target.x() - offX) * pull;
        velY = (target.y() - offY) * pull;
        move();
        bank();
        speed = Mth.lerp(0.06f, speed, WarpTunnel.BOOST_SPEED);
    }

    private static void follow() {
        prevOffX = offX;
        prevOffY = offY;
        float newX = Mth.lerp(0.5f, offX, followX), newY = Mth.lerp(0.5f, offY, followY);
        velX = newX - offX;
        velY = newY - offY;
        offX = newX;
        offY = newY;
        bank();
        double gap = Math.max(0, followProgress - progress);
        speed = (float) Math.min(gap, Math.max(gap * 0.5, WarpTunnel.CRUISE_SPEED * 0.5));
        advance();
    }

    private static void move() {
        prevOffX = offX;
        prevOffY = offY;
        offX += velX;
        offY += velY;
    }

    private static void bank() {
        prevRoll = roll;
        roll = Mth.lerp(0.12f, roll, Mth.clamp(-velX * 22f, -20f, 20f));
    }

    private static void advance() {
        prevProgress = progress;
        progress += speed;
        prevWarp = warp;
        warp = Math.min(WarpTunnel.MAX_WARP_DISTANCE, warp + layout.warpDistance(speed));
        crossings();
    }

    private static void crossings() {
        for (; WarpTunnel.goldRingPassedAt(seed, nextGoldRing) <= progress; nextGoldRing++) {
            WarpTunnel.GoldRing ring = WarpTunnel.goldRing(seed, nextGoldRing);
            if (ring == null || !WarpTunnel.touches(ring, offX, offY)) continue;
            stamina.collectRing();
            COLLECTED_RINGS.put(ring.index(), ticks);
            boostUntil = ticks + 50;
            play(STAT_UP, 1f, 1f);
            play(STAT_UP, 1f, 1f);
        }
        while (true) {
            WarpTunnel.Wormhole w = layout.wormhole(nextWormhole);
            if (w.distance() > progress) return;
            if (layout.isUncharted(w.index()) || WarpTunnel.touches(w, offX, offY)) {
                TOUCHED.put(w.index(), ticks);
                flash = Math.max(flash, 0.3f);
                play(SoundEvents.BEACON_POWER_SELECT, 1.5f + 0.1f * w.rings(), 1f);
                play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1f);
            } else if (Mth.square(w.x() - offX) + Mth.square(w.y() - offY) < 16 * 16) {
                play(SoundEvents.ENDER_DRAGON_FLAP, 1.7f, 0.5f);
            }
            nextWormhole++;
        }
    }

    private static void keepSeated(LocalPlayer player) {
        if (mount == RideLegend.NONE || player.getVehicle() != null) return;
        player.level().getEntitiesOfClass(PokemonEntity.class, player.getBoundingBox().inflate(12)).stream()
                .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(player)))
                .ifPresent(nearest -> player.startRiding(nearest, true));
    }

    private static void clearGhosts(Minecraft mc, LocalPlayer player) {
        for (PokemonEntity ghost : mc.level.getEntitiesOfClass(PokemonEntity.class, player.getBoundingBox().inflate(256),
                e -> !e.isVehicle() && e != player.getVehicle()
                        && (player.getVehicle() != null || e.distanceToSqr(player) > 8 * 8))) {
            mc.level.removeEntity(ghost.getId(), Entity.RemovalReason.DISCARDED);
        }
    }

    private static void aim(Minecraft mc, LocalPlayer player) {
        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        float lookAhead = 40f;
        float dx = WarpTunnel.bendX(lookAhead, ticks) + velX * 16f;
        float dy = WarpTunnel.bendY(lookAhead, ticks) + velY * 16f;
        float targetYaw = Mth.clamp((float) (-Mth.atan2(dx, lookAhead) * Mth.RAD_TO_DEG), -35f, 35f);
        float targetPitch = Mth.clamp((float) (-Mth.atan2(dy, Mth.sqrt(dx * dx + lookAhead * lookAhead)) * Mth.RAD_TO_DEG), -25f, 25f);
        float prevYaw = facingYaw, prevPitch = facingPitch;
        facingYaw = Mth.lerp(0.07f, facingYaw, targetYaw);
        facingPitch = Mth.lerp(0.07f, facingPitch, targetPitch);

        player.yRotO = 0f;
        player.setYRot(0f);
        player.xRotO = 0f;
        player.setXRot(0f);
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;
        vehicle.yRotO = prevYaw;
        vehicle.setYRot(facingYaw);
        vehicle.xRotO = prevPitch;
        vehicle.setXRot(facingPitch);
        vehicle.setYHeadRot(facingYaw);
        vehicle.setYBodyRot(facingYaw);
        if (vehicle instanceof LivingEntity living) {
            living.yHeadRotO = prevYaw;
            living.yBodyRotO = prevYaw;
        }
        vehicle.setDeltaMovement(0, 0, 0);
    }

    private static void seedStreaks(RandomSource random) {
        STREAKS.clear();
        for (int i = 0; i < 150; i++) {
            Streak s = new Streak();
            respawnStreak(s, random);
            s.z = s.prevZ = -40 + random.nextFloat() * (STREAK_SPAWN_Z + 40);
            STREAKS.add(s);
        }
    }

    private static void respawnStreak(Streak s, RandomSource random) {
        s.angle = random.nextFloat() * Mth.TWO_PI;
        s.radius = random.nextFloat() < 0.75f
                ? WarpTunnel.TUNNEL_RADIUS * (0.7f + random.nextFloat() * 0.25f)
                : WarpTunnel.TUNNEL_RADIUS * random.nextFloat() * 0.65f;
        s.z = s.prevZ = STREAK_SPAWN_Z + random.nextFloat() * 40;
        s.length = 6 + random.nextFloat() * 18;
        s.width = 0.15f + random.nextFloat() * 0.35f;
        s.speedFactor = 1.1f + random.nextFloat() * 1.4f;
        s.brightness = 0.35f + random.nextFloat() * 0.65f;
    }

    private static void tickStreaks() {
        RandomSource random = RandomSource.create();
        for (Streak s : STREAKS) {
            s.prevZ = s.z;
            s.z -= speed * s.speedFactor;
            if (s.z < -60) respawnStreak(s, random);
        }
    }

    private static void spawnTrail(LocalPlayer player) {
        Entity body = player.getVehicle() != null ? player.getVehicle() : player;
        RandomSource random = player.getRandom();
        for (int i = 0; i < 2; i++) {
            player.level().addParticle(ParticleTypes.END_ROD,
                    body.getX() + random.triangle(0, body.getBbWidth()),
                    body.getY() + random.nextDouble() * body.getBbHeight(),
                    body.getZ() - body.getBbWidth() * 0.5,
                    random.triangle(0, 0.03), random.triangle(0, 0.03), -0.6 - speed * 0.3);
        }
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
