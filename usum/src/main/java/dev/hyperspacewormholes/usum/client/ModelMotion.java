package dev.hyperspacewormholes.usum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.hyperspacewormholes.usum.ModelMotionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The model motions the server sends (ModelMotionPayload): per entity the latest curve, played on the level's game time from its start and applied to the pose of the entity's model while Cobblemon draws it (mixin.ModelMotionMixin).
 * The curve is a shift along the body's facing, up and to its right, a turn about the middle of its hitbox and a scale along its side, up and facing axes about its feet (0 hides it). Only the drawing moves, not the entity, hitbox or position; a curve is dropped after its last key.
 */
public final class ModelMotion {
    private static final Map<Integer, Motion> MOTIONS = new HashMap<>();
    private static final Deque<Boolean> PUSHED = new ArrayDeque<>();
    private static final float MIN_SCALE = 1e-4f;

    private record Motion(double start, float[] keys) {
        double end() {
            return start + keys[keys.length - ModelMotionPayload.STRIDE] * 20.0;
        }
    }

    private ModelMotion() {
    }

    public static void add(ModelMotionPayload payload) {
        double now = now(0);
        List<Float> list = payload.keys();
        if (now < 0 || list.size() < ModelMotionPayload.STRIDE) return;
        float[] keys = new float[list.size() - list.size() % ModelMotionPayload.STRIDE];
        for (int i = 0; i < keys.length; i++) keys[i] = list.get(i);
        MOTIONS.put(payload.entity(), new Motion(now + payload.delay() * 20.0, keys));
    }

    public static void tick() {
        double now = now(0);
        MOTIONS.values().removeIf(motion -> now < 0 || now > motion.end() + 1);
    }

    public static void clear() {
        MOTIONS.clear();
        PUSHED.clear();
    }

    public static void begin(LivingEntity entity, float partialTicks, PoseStack pose) {
        PUSHED.push(push(entity, partialTicks, pose));
    }

    public static void end(PoseStack pose) {
        if (!PUSHED.isEmpty() && PUSHED.pop()) pose.popPose();
    }

    private static boolean push(LivingEntity entity, float partialTicks, PoseStack pose) {
        float[] v = values(entity, partialTicks);
        if (v == null) return false;
        pose.pushPose();
        apply(pose, v, Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot), entity.getBbHeight() / 2);
        return true;
    }

    /**
     * The model motion's own transform for an entity right now, relative to its feet in world axes (what push puts on
     * the drawing pose), into {@code out}; false (out untouched) when no motion plays. client.BodyLocators puts the
     * body locators through it so effects on them move with the drawn model.
     */
    public static boolean matrix(LivingEntity entity, float partialTicks, Matrix4f out) {
        float[] v = values(entity, partialTicks);
        if (v == null) return false;
        PoseStack pose = new PoseStack();
        apply(pose, v, Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot), entity.getBbHeight() / 2);
        out.set(pose.last().pose());
        return true;
    }

    /** The key values (forward, up, side, pitch, yaw, roll, scale side, up, forward at 1..9) at this moment. */
    private static float @Nullable [] values(LivingEntity entity, float partialTicks) {
        if (MOTIONS.isEmpty()) return null;
        Motion motion = MOTIONS.get(entity.getId());
        if (motion == null) return null;
        double now = now(partialTicks);
        double t = (now - motion.start()) / 20.0;
        float[] k = motion.keys();
        int n = k.length / ModelMotionPayload.STRIDE;
        if (now < 0 || t < k[0] || t > k[(n - 1) * ModelMotionPayload.STRIDE]) return null;
        float[] v = new float[ModelMotionPayload.STRIDE];
        for (int i = 0; i < n; i++) {
            int a = i * ModelMotionPayload.STRIDE;
            if (i == n - 1 || t <= k[a + ModelMotionPayload.STRIDE]) {
                int b = i == n - 1 ? a : a + ModelMotionPayload.STRIDE;
                float span = k[b] - k[a];
                float f = span > 0 ? (float) Mth.clamp((t - k[a]) / span, 0, 1) : 0;
                for (int j = 1; j < ModelMotionPayload.STRIDE; j++) v[j] = Mth.lerp(f, k[a + j], k[b + j]);
                break;
            }
        }
        return v;
    }

    /**
     * Puts key values v (as values() gives them) on a pose whose origin is the entity's feet: shift along the body's
     * facing, up and to its right, turn about the middle of its hitbox, scale about its feet.
     */
    public static void apply(PoseStack pose, float[] v, float bodyYaw, float middle) {
        pose.translate(0, middle, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        pose.translate(-v[3], v[2], v[1]);
        pose.mulPose(Axis.YP.rotationDegrees(v[5]));
        pose.mulPose(Axis.XP.rotationDegrees(-v[4]));
        pose.mulPose(Axis.ZP.rotationDegrees(v[6]));
        if (v[7] != 1 || v[8] != 1 || v[9] != 1) {
            pose.translate(0, -middle, 0);
            pose.scale(Math.max(v[7], MIN_SCALE), Math.max(v[8], MIN_SCALE), Math.max(v[9], MIN_SCALE));
            pose.translate(0, middle, 0);
        }
        pose.mulPose(Axis.YP.rotationDegrees(bodyYaw));
        pose.translate(0, -middle, 0);
    }

    private static double now(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? -1 : mc.level.getGameTime() + partialTick;
    }
}
