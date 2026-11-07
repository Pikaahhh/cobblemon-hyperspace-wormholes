package dev.hyperspacewormholes.wormholes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.network.RiderEmergeDonePayload;
import dev.hyperspacewormholes.wormholes.network.RiderEmergePayload;
import dev.hyperspacewormholes.wormholes.ride.RiderArrival;
import dev.hyperspacewormholes.wormholes.ride.UltraWarpRules;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Riders coming out of a wormhole start tiny at its mouth and grow as they glide out. If it's you, the camera watches
 * from the side with the HUD hidden.
 */
public final class RiderArrivalClient {
    private static final double AIM_HEIGHT = 1.5;
    private static final float LEVEL_OUT_FROM = 0.8f;
    private static final float CHASE_CLOSE_FROM = 0.55f;
    private static final Map<Integer, Emergence> ACTIVE = new HashMap<>();
    private static @Nullable Emergence watching;

    private static final class Emergence {
        final int riderId;
        final List<Integer> passengerIds;
        final Vec3 from, to;
        final Vec3 straight, descent;
        final float yaw;
        final int emergeTicks, settleTicks;
        final boolean local;
        final boolean airborne;
        int age;
        int cameraSide;
        final List<Entity> held = new ArrayList<>();

        Emergence(RiderEmergePayload p, boolean local) {
            this.riderId = p.riderId();
            this.passengerIds = List.copyOf(p.passengerIds());
            Vec3 forward = flat(p.landing().subtract(p.mouth()));
            float pitch = p.airborne() ? 0f : RiderArrival.EXIT_PITCH * Mth.DEG_TO_RAD;
            Vec3 axis = forward.scale(Mth.cos(pitch)).add(0, -Mth.sin(pitch), 0);
            this.from = p.mouth().subtract(axis.scale(1.5));
            this.to = p.landing();
            Vec3 end = to.add(0, AIM_HEIGHT, 0);
            double across = Math.sqrt(Mth.square(end.x - p.mouth().x) + Mth.square(end.z - p.mouth().z));
            double drop = Math.max(0, from.y - end.y);
            this.airborne = p.airborne();
            this.straight = airborne ? from.lerp(end, 1 / 3.0) : p.mouth().add(axis.scale(across * 0.75));
            this.descent = airborne ? from.lerp(end, 2 / 3.0) : end.add(0, Math.max(1.5, drop * 0.6), 0);
            this.emergeTicks = Math.max(1, p.emergeTicks());
            this.settleTicks = p.settleTicks();
            this.local = local;
            this.yaw = (float) (Mth.atan2(-forward.x, forward.z) * Mth.RAD_TO_DEG);
        }

        float progress(float partialTick) {
            return Mth.clamp((age + partialTick) / emergeTicks, 0f, 1f);
        }

        Vec3 pointAt(float t) {
            Vec3 aim = bezier(from, straight, descent, to.add(0, AIM_HEIGHT, 0), smooth(t));
            return aim.subtract(0, AIM_HEIGHT * scaleAt(t), 0);
        }

        float pitchAt(float t) {
            Vec3 ahead = pointAt(Math.min(1f, t + 0.02f)).subtract(pointAt(Math.max(0f, t - 0.02f)));
            double across = Math.sqrt(ahead.x * ahead.x + ahead.z * ahead.z);
            if (across < 1e-4 && Math.abs(ahead.y) < 1e-4) return 0f;
            float pitch = Mth.clamp((float) (Mth.atan2(-ahead.y, across) * Mth.RAD_TO_DEG), -35f, 35f);
            return pitch * (1 - smooth(Mth.clamp((t - LEVEL_OUT_FROM) / (1 - LEVEL_OUT_FROM), 0f, 1f)));
        }

        float scaleAt(float t) {
            return Mth.lerp(smooth(Math.min(1f, t / 0.6f)), 0.04f, 1f);
        }
    }

    private RiderArrivalClient() {}

    public static void start(RiderEmergePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        boolean local = mc.player != null && (mc.player.getId() == payload.riderId() || payload.passengerIds().contains(mc.player.getId()));
        Emergence previous = ACTIVE.remove(payload.riderId());
        if (previous != null) finish(previous, false);
        Emergence e = new Emergence(payload, local);
        ACTIVE.put(payload.riderId(), e);
        if (local) {
            watching = e;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    public static boolean isWatching() {
        return watching != null;
    }

    public static void tick(Minecraft mc) {
        if (ACTIVE.isEmpty()) return;
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        if (mc.screen instanceof ReceivingLevelScreen) return;

        for (Iterator<Emergence> it = ACTIVE.values().iterator(); it.hasNext(); ) {
            Emergence e = it.next();
            Entity rider = mc.level.getEntity(e.riderId);
            if (rider != null) {
                if (e.local && mc.player.getVehicle() == null && e.age > 2) {
                    mc.level.getEntitiesOfClass(PokemonEntity.class, new AABB(e.to, e.to).inflate(4))
                            .stream().findFirst().ifPresent(mount -> mc.player.startRiding(mount, true));
                }
                hold(e, rider);
                if (rider.getVehicle() != null) hold(e, rider.getVehicle());
            }
            for (int id : e.passengerIds) {
                Entity passenger = mc.level.getEntity(id);
                if (passenger != null) hold(e, passenger);
            }
            if (e.local) {
                CameraType wanted = e.airborne && e.progress(0) >= 0.93f
                        ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK;
                if (mc.options.getCameraType() != wanted) mc.options.setCameraType(wanted);
            }
            e.age++;
            if (e.age >= e.emergeTicks + e.settleTicks) {
                it.remove();
                finish(e, true);
            }
        }
    }

    private static void hold(Emergence e, Entity entity) {
        if (e.held.contains(entity)) return;
        e.held.add(entity);
        UltraWarpRules.hold(entity);
    }

    private static void finish(Emergence e, boolean tellServer) {
        e.held.forEach(UltraWarpRules::release);
        e.held.clear();
        if (e != watching) return;
        watching = null;
        Minecraft mc = Minecraft.getInstance();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (e.airborne && mc.player != null) {
            mc.player.setYRot(e.yaw);
            mc.player.yRotO = e.yaw;
            mc.player.setXRot(0f);
            mc.player.xRotO = 0f;
            mc.player.setYHeadRot(e.yaw);
        }
        if (tellServer && mc.player != null && mc.player.getId() == e.riderId && mc.getConnection() != null) {
            ClientPlayNetworking.send(RiderEmergeDonePayload.INSTANCE);
        }
    }

    public static void reset() {
        for (Emergence e : ACTIVE.values()) finish(e, false);
        ACTIVE.clear();
    }

    private static @Nullable Emergence of(Entity entity) {
        if (ACTIVE.isEmpty()) return null;
        for (Emergence e : ACTIVE.values()) {
            if (e.held.contains(entity)) return e;
        }
        return null;
    }
    public static boolean anyEmerging() {
        return !ACTIVE.isEmpty();
    }

    public static boolean isEmerging(Entity entity) {
        return of(entity) != null;
    }

    public static boolean transform(Entity entity, double x, double y, double z, float partialTick, PoseStack poseStack) {
        Emergence e = of(entity);
        if (e == null) return false;
        Vec3 pos = entity.getPosition(partialTick);
        Vec3 cam = pos.subtract(x, y, z);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (cam.distanceToSqr(camera) > 0.25) return false;

        Entity body = mountOf(entity);
        Vec3 pivot = body.getPosition(partialTick);
        float t = e.progress(partialTick);
        Vec3 at = e.pointAt(t);
        float s = e.scaleAt(t);
        poseStack.pushPose();
        poseStack.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        poseStack.scale(s, s, s);
        float bodyYaw = body instanceof LivingEntity living
                ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot) : body.getViewYRot(partialTick);
        float turn = Mth.wrapDegrees(e.yaw - bodyYaw);
        float yawRad = e.yaw * Mth.DEG_TO_RAD;
        poseStack.translate(0, AIM_HEIGHT, 0);
        poseStack.mulPose(new Quaternionf().rotationAxis(e.pitchAt(t) * Mth.DEG_TO_RAD, Mth.cos(yawRad), 0, Mth.sin(yawRad)));
        poseStack.mulPose(Axis.YP.rotationDegrees(-turn));
        poseStack.translate(0, -AIM_HEIGHT, 0);
        poseStack.translate(cam.x - pivot.x, cam.y - pivot.y, cam.z - pivot.z);
        return true;
    }

    public static double @Nullable [] camera(float partialTick) {
        Emergence e = watching;
        Minecraft mc = Minecraft.getInstance();
        if (e == null || mc.level == null || mc.player == null) return null;
        if (e.airborne) return chaseCamera(e, mc, partialTick);
        Vec3 forward = flat(e.to.subtract(e.from));
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        Vec3 middle = e.from.lerp(e.to.add(0, 1.5, 0), 0.5);
        if (e.cameraSide == 0) {
            double left = clear(middle, cameraSpot(middle, forward, side, 1, 0), mc);
            double right = clear(middle, cameraSpot(middle, forward, side, -1, 0), mc);
            e.cameraSide = left >= right ? 1 : -1;
        }
        float t = e.progress(partialTick);
        Vec3 wanted = cameraSpot(middle, forward, side, e.cameraSide, smooth(t));
        Vec3 spot = pullIn(middle, wanted, mc);

        Entity rider = mc.level.getEntity(e.riderId);
        double height = rider != null ? mountOf(rider).getBbHeight() : 1.8;
        Vec3 target = e.pointAt(t).add(0, height * 0.5 * e.scaleAt(t), 0).lerp(middle, 0.35);
        Vec3 look = target.subtract(spot);
        double yaw = Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG;
        double pitch = -Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG;
        return new double[]{spot.x, spot.y, spot.z, yaw, pitch};
    }

    private static double[] chaseCamera(Emergence e, Minecraft mc, float partialTick) {
        float t = e.progress(partialTick);
        float s = e.scaleAt(t);
        Vec3 forward = flat(e.to.subtract(e.from));
        Entity rider = mc.level.getEntity(e.riderId);
        Vec3 at = e.pointAt(t);
        Vec3 eyes = rider == null ? at.add(0, 1.6 * s, 0)
                : at.add(mc.player.getEyePosition(partialTick).subtract(mountOf(rider).getPosition(partialTick)).scale(s));
        float close = smooth(Mth.clamp((t - CHASE_CLOSE_FROM) / (1 - CHASE_CLOSE_FROM), 0f, 1f));
        double back = (1.2 + 7.0 * s) * (1 - close);
        double up = (0.3 + 1.8 * s) * (1 - close);
        Vec3 spot = eyes.subtract(forward.scale(back)).add(0, up, 0);
        double pitch = Mth.atan2(up, back + 6) * Mth.RAD_TO_DEG;
        return new double[]{spot.x, spot.y, spot.z, e.yaw, pitch};
    }

    private static Vec3 cameraSpot(Vec3 middle, Vec3 forward, Vec3 side, int sideSign, float drift) {
        return middle.add(side.scale(9.0 * sideSign)).add(forward.scale(3 + 2.5 * drift)).add(0, 2.5, 0);
    }

    private static double clear(Vec3 from, Vec3 to, Minecraft mc) {
        return pullIn(from, to, mc).distanceTo(from);
    }

    private static Vec3 pullIn(Vec3 from, Vec3 to, Minecraft mc) {
        BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) return to;
        Vec3 dir = to.subtract(from).normalize();
        return hit.getLocation().subtract(dir.scale(0.4));
    }

    private static Entity mountOf(Entity entity) {
        return entity.getVehicle() != null ? entity.getVehicle() : entity;
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    private static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u) {
        double v = 1 - u;
        return a.scale(v * v * v).add(b.scale(3 * v * v * u)).add(c.scale(3 * v * u * u)).add(d.scale(u * u * u));
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }
}
