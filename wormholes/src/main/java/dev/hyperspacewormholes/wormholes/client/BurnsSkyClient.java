package dev.hyperspacewormholes.wormholes.client;

import com.cobblemon.mod.common.client.render.MatrixWrapper;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PrimaryAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockActiveAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hyperspacewormholes.wormholes.client.render.Domes;
import dev.hyperspacewormholes.wormholes.client.render.OwnPass;
import dev.hyperspacewormholes.wormholes.client.render.WarpRenderTypes;
import dev.hyperspacewormholes.wormholes.network.BurnsSkyPayload;
import dev.hyperspacewormholes.wormholes.network.BurnsSkyStopPayload;
import dev.hyperspacewormholes.wormholes.zmove.BurnsSkyTimeline;
import dev.hyperspacewormholes.wormholes.zmove.LightThatBurnsTheSky;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The client side of Light That Burns the Sky (zmove/LightThatBurnsTheSky), on Ultra Sun's timeline (BurnsSkyTimeline).
 * Necrozma plays a retargeted copy of the game's animation as a Cobblemon primary animation, and PosableModelRoarMixin
 * hands over the posed model each frame so the light ball follows its mouth and is pushed clear of its cubes.
 * Once the world is drawn, the darkness spheres and then the effects (BurnsSkyEffects) go down in a pass of our own;
 * the screen fills are a HUD element and the camera shakes on the game's cues. It reaches players within 160 blocks of
 * the battle, fading from 128.
 */
public final class BurnsSkyClient {
    private static final double DARK_RANGE = 160;
    private static final double DARK_FADE_FROM = 128;
    private static final int DARK_TICKS = 100;
    private static final int LIGHT_RETURN_TICKS = 12;
    private static final float MOTION_FADE_IN = 0.01f;
    private static final float SHADE_AROUND = 1.5f;
    private static final float SHADE_BEYOND_CAMERA = 3f;
    private static final float SHADE_EDGE = 4f;
    private static final float SHADE_MAX = 160f;
    private static final int SHADE_SHELLS = 6;
    private static final double SHADE_CLEARANCE = 2;
    private static final float SHADE_FROM = 48f;
    private static final double SHAKE_RANGE = 64;
    private static final double SHAKE_FADE_FROM = 40;
    private static final float SHAKE_DEGREES = 0.06f;
    private static final float SHAKE_RATE = 0.12f;
    private static final float HEAD_RISE_TICKS = 6f;
    private static final double MOUTH_TIP = 1.8;
    private static final double TIP_SWITCH = 0.15;
    private static final float TRACKING = 0.6f;
    private static final double BODY_MARGIN = 0.3;
    private static final int PROJECTIONS = 12;
    private static final double PUSH_STEP = 0.25;
    private static final double MAX_PUSH = 12;
    private static final double PUSH_EASE = 0.08;
    private static final String MOTIONS = "necrozma_burns_sky";

    record Box(Vec3 centre, Vector3f[] axes, float[] extents, AABB bounds, boolean jaw) {
        Vec3 front(Vec3 along) {
            int best = 0;
            double bestDot = 0;
            for (int i = 0; i < 3; i++) {
                Vector3f axis = axes[i];
                double dot = axis.x * along.x + axis.y * along.y + axis.z * along.z;
                if (Math.abs(dot) > Math.abs(bestDot)) {
                    best = i;
                    bestDot = dot;
                }
            }
            Vector3f axis = axes[best];
            double reach = Math.signum(bestDot) * extents[best];
            return centre.add(axis.x * reach, axis.y * reach, axis.z * reach);
        }
    }

    public static final class Run {
        public final BurnsSkyPayload payload;
        public final long start;
        @Nullable Vec3 mouthOffset, release;
        int tipBox = -1;
        Vec3 bodyAt = Vec3.ZERO;
        double push;
        List<Box> body = List.of();
        boolean animated;

        Run(BurnsSkyPayload payload, long start) {
            this.payload = payload;
            this.start = start;
        }

        Vec3 dir() {
            return payload.dir();
        }

        Vec3 mouth(Minecraft mc, float partialTick) {
            if (mouthOffset != null) return userAt(mc, partialTick).add(mouthOffset);
            Entity user = mc.level == null ? null : mc.level.getEntity(payload.userId());
            return user != null ? LightThatBurnsTheSky.mouth(user, dir()) : payload.mouth();
        }

        Vec3 held(Minecraft mc, double age, float partialTick) {
            Vec3 base = BurnsSkyTimeline.held(age, mouth(mc, partialTick), dir());
            return base.add(BurnsSkyTimeline.holdDirection(dir()).scale(push * BurnsSkyTimeline.outOfMouth(age)));
        }

        Vec3 userAt(Minecraft mc, float partialTick) {
            Entity user = mc.level == null ? null : mc.level.getEntity(payload.userId());
            return user != null ? user.getPosition(partialTick) : bodyAt;
        }

        boolean touchesBody(Vec3 centre, double half, Vec3 userAt) {
            Vec3 point = centre.subtract(userAt).add(bodyAt);
            double radius = half * Math.sqrt(2);
            for (Box box : body) {
                if (!box.bounds().inflate(radius + BODY_MARGIN, half + BODY_MARGIN, radius + BODY_MARGIN).contains(point)) continue;
                if (gap(box, point, radius, half) <= BODY_MARGIN) return true;
            }
            return false;
        }

        private static double gap(Box box, Vec3 centre, double radius, double half) {
            Vec3 onBall = centre, onBox = centre;
            for (int i = 0; i < PROJECTIONS; i++) {
                Vec3 d = onBall.subtract(box.centre());
                onBox = box.centre();
                for (int j = 0; j < 3; j++) {
                    Vector3f axis = box.axes()[j];
                    double along = Mth.clamp(d.x * axis.x + d.y * axis.y + d.z * axis.z, -box.extents()[j], box.extents()[j]);
                    onBox = onBox.add(axis.x * along, axis.y * along, axis.z * along);
                }
                double x = onBox.x - centre.x, z = onBox.z - centre.z, across = Math.sqrt(x * x + z * z);
                double scale = across > radius ? radius / across : 1;
                onBall = new Vec3(centre.x + x * scale, Mth.clamp(onBox.y, centre.y - half, centre.y + half), centre.z + z * scale);
            }
            return onBox.distanceTo(onBall);
        }

        Vec3 targetFeet() {
            return payload.targetFeet();
        }

        public Vec3 ball(Minecraft mc, double age, float partialTick) {
            if (age < BurnsSkyTimeline.THROW) return held(mc, age, partialTick);
            Vec3 from = release != null ? release : held(mc, BurnsSkyTimeline.THROW, partialTick);
            return BurnsSkyTimeline.thrown(age, from, targetFeet());
        }

        Vec3 battleCentre() {
            return payload.mouth().add(payload.targetCentre()).scale(0.5);
        }
    }

    private static final Map<Integer, Run> RUNS = new HashMap<>();
    private static final OwnPass PASS = new OwnPass();
    private static Vec3 darkCentre = Vec3.ZERO;
    private static @Nullable Run darkRun;
    private static final Matrix4f VIEW = new Matrix4f(), PROJECTION = new Matrix4f();
    private static Vec3 viewCamera = Vec3.ZERO;

    private BurnsSkyClient() {}

    public static Map<Integer, Run> runs() {
        return RUNS;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BurnsSkyPayload.TYPE, (payload, context) -> start(payload));
        ClientPlayNetworking.registerGlobalReceiver(BurnsSkyStopPayload.TYPE, (payload, context) -> RUNS.remove(payload.userId()));
        ClientTickEvents.END_CLIENT_TICK.register(BurnsSkyClient::tick);
        WorldRenderEvents.LAST.register(BurnsSkyClient::renderWorld);
        HudRenderCallback.EVENT.register(BurnsSkyClient::renderHud);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RUNS.clear());
    }

    private static void start(BurnsSkyPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Run run = new Run(payload, mc.level.getGameTime());
        RUNS.put(payload.userId(), run);
        run.animated = animate(mc.level.getEntity(payload.userId()));
    }

    private static boolean animate(@Nullable Entity user) {
        if (!(user instanceof PokemonEntity pokemon) || !(pokemon.getDelegate() instanceof PosableState state)) return false;
        BedrockAnimation animation = BedrockAnimationRepository.INSTANCE.getAnimationOrNull(MOTIONS, "animation." + MOTIONS + ".ultra");
        if (animation == null) return false;
        int[] last = BurnsSkyTimeline.FILLS[BurnsSkyTimeline.FILLS.length - 1];
        float out = (float) last[1] / BurnsSkyTimeline.END;
        float gone = (float) last[2] / BurnsSkyTimeline.END;
        state.addPrimaryAnimation(new PrimaryAnimation(new BedrockActiveAnimation(animation),
                t -> Mth.clamp(Math.min(t / MOTION_FADE_IN, (gone - t) / (gone - out)), 0f, 1f), Set.of(), true));
        return true;
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null) {
            RUNS.clear();
            return;
        }
        long now = mc.level.getGameTime();
        for (Iterator<Run> it = RUNS.values().iterator(); it.hasNext(); ) {
            Run run = it.next();
            if (now - run.start > BurnsSkyTimeline.END + LIGHT_RETURN_TICKS) it.remove();
        }
    }

    private static double age(Run run, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() - run.start + partialTick;
    }

    public static float headLift(Entity entity, float partialTick) {
        Run run = RUNS.get(entity.getId());
        if (run == null || run.animated) return 0f;
        double age = age(run, partialTick);
        float rise = (float) Math.min(1, age / HEAD_RISE_TICKS);
        float settle = (float) Math.min(1, 1 - (age - BurnsSkyTimeline.THROW) / HEAD_RISE_TICKS);
        float amount = Mth.clamp(Math.min(rise, settle), 0f, 1f);
        return amount * amount * (3 - 2 * amount);
    }

    public static void trackMouth(Entity entity, PosableModel model, PosableState state) {
        Run run = RUNS.get(entity.getId());
        if (run == null) return;
        Vec3 headWorld = Bones.world(entity, model, state, "head");
        Vec3 jawWorld = Bones.world(entity, model, state, "jaw");
        if (headWorld == null || jawWorld == null) return;
        Vec3 along = jawWorld.subtract(headWorld);
        if (along.lengthSqr() < 1e-6) return;
        Vec3 at = entity.getPosition(state.getPartialTicks());
        run.body = body(entity, model, state);
        run.bodyAt = at;
        if (run.tipBox >= run.body.size() || run.tipBox >= 0 && !run.body.get(run.tipBox).jaw()) run.tipBox = -1;
        Vec3 ahead = along.normalize();
        int frontBox = -1;
        double front = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < run.body.size(); i++) {
            Box box = run.body.get(i);
            if (!box.jaw()) continue;
            double reach = box.front(ahead).subtract(headWorld).dot(ahead);
            if (reach > front) {
                front = reach;
                frontBox = i;
            }
        }
        if (run.tipBox < 0 || frontBox >= 0 && frontBox != run.tipBox
                && front > run.body.get(run.tipBox).front(ahead).subtract(headWorld).dot(ahead) + TIP_SWITCH) run.tipBox = frontBox;
        Vec3 tip = run.tipBox >= 0 ? run.body.get(run.tipBox).front(ahead) : jawWorld.add(along.scale(MOUTH_TIP));
        Vec3 offset = tip.subtract(at);
        run.mouthOffset = run.mouthOffset == null ? offset : run.mouthOffset.lerp(offset, TRACKING);
        double age = age(run, state.getPartialTicks());
        if (age >= BurnsSkyTimeline.THROW || BurnsSkyTimeline.outOfMouth(age) <= 0) return;
        double half = BurnsSkyTimeline.ballRadius(age) * BurnsSkyTimeline.BALL_OUTER;
        Vec3 out = BurnsSkyTimeline.holdDirection(run.dir());
        Vec3 from = BurnsSkyTimeline.held(age, at.add(run.mouthOffset), run.dir());
        double need = 0;
        while (need < MAX_PUSH && run.touchesBody(from.add(out.scale(need)), half, at)) need += PUSH_STEP;
        run.push = need >= run.push ? need : Mth.lerp(PUSH_EASE, run.push, need);
    }

    private static List<Box> body(Entity entity, PosableModel model, PosableState state) {
        MatrixWrapper root = state.getLocatorStates().get("root");
        if (root == null) return List.of();
        PoseStack pose = new PoseStack();
        pose.last().pose().set(root.getMatrix()).rotateZ(-180f * Mth.DEG_TO_RAD);
        Vec3 at = entity.getPosition(state.getPartialTicks());
        List<Box> boxes = new ArrayList<>();
        for (Bone bone : model.getRootPart().getChildren().values()) {
            if (!(bone instanceof ModelPart part) || !part.visible) continue;
            boolean jawPart = bone == model.getRelevantPartsByName().get("jaw");
            part.visit(pose, (cubePose, path, index, cube) -> {
                float[] lo = {cube.minX / 16f, cube.minY / 16f, cube.minZ / 16f};
                float[] hi = {cube.maxX / 16f, cube.maxY / 16f, cube.maxZ / 16f};
                Matrix4f m = cubePose.pose();
                Vector3f middle = m.transformPosition((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2, new Vector3f());
                Vector3f[] axes = new Vector3f[3];
                float[] extents = new float[3];
                Vector3f half = new Vector3f();
                for (int i = 0; i < 3; i++) {
                    Vector3f axis = m.transformDirection(i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0, new Vector3f());
                    float scale = axis.length();
                    extents[i] = (hi[i] - lo[i]) / 2 * scale;
                    axes[i] = scale > 1e-6f ? axis.div(scale) : axis;
                    half.add(Math.abs(axes[i].x) * extents[i], Math.abs(axes[i].y) * extents[i], Math.abs(axes[i].z) * extents[i]);
                }
                Vec3 centre = at.add(middle.x, middle.y, middle.z);
                boxes.add(new Box(centre, axes, extents, new AABB(centre.x - half.x, centre.y - half.y, centre.z - half.z,
                        centre.x + half.x, centre.y + half.y, centre.z + half.z), jawPart || path.endsWith("/jaw")));
            });
        }
        return boxes;
    }

    public static @Nullable float[] shake(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (RUNS.isEmpty() || mc.level == null || mc.player == null) return null;
        float best = 0, rate = 0;
        long seed = 0;
        Vec3 eye = mc.player.getEyePosition(partialTick);
        for (Run run : RUNS.values()) {
            double distance = eye.distanceTo(run.battleCentre());
            if (distance > SHAKE_RANGE) continue;
            double near = distance < SHAKE_FADE_FROM ? 1 : (SHAKE_RANGE - distance) / (SHAKE_RANGE - SHAKE_FADE_FROM);
            double age = age(run, partialTick);
            for (float[] cue : BurnsSkyTimeline.SHAKES) {
                if (age < cue[0] || age > cue[1]) continue;
                float amplitude = (float) (BurnsSkyTimeline.lerp(age, cue[0], cue[1], cue[2], cue[3]) * near);
                if (amplitude > best) {
                    best = amplitude;
                    rate = cue[4];
                    seed = run.payload.userId();
                }
            }
        }
        if (best <= 0) return null;
        double t = (mc.level.getGameTime() + partialTick) * rate * SHAKE_RATE;
        float degrees = best * SHAKE_DEGREES;
        return new float[]{(float) (noise(t, seed) * degrees), (float) (noise(t + 17.3, seed + 1) * degrees)};
    }

    private static double noise(double t, long seed) {
        long i = (long) Math.floor(t);
        double f = t - i;
        double a = BurnsSkyEffects.unit(BurnsSkyEffects.mix(seed * 31 + i)) * 2 - 1;
        double b = BurnsSkyEffects.unit(BurnsSkyEffects.mix(seed * 31 + i + 1)) * 2 - 1;
        return Mth.lerp(f * f * (3 - 2 * f), a, b);
    }

    private static float darkness(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (RUNS.isEmpty() || mc.level == null) return 0;
        float best = 0;
        int lift = BurnsSkyTimeline.FILLS[BurnsSkyTimeline.FILLS.length - 1][1];
        for (Run run : RUNS.values()) {
            double age = age(run, partialTick);
            double dark;
            if (age < BurnsSkyTimeline.CHARGE) {
                dark = 0;
            } else if (age < lift) {
                dark = (age - BurnsSkyTimeline.CHARGE) / DARK_TICKS;
            } else {
                dark = 1 - (age - lift) / LIGHT_RETURN_TICKS;
            }
            dark = Mth.clamp(dark, 0, 1);
            double distance = viewCamera.distanceTo(run.battleCentre());
            if (distance > DARK_RANGE) continue;
            if (distance > DARK_FADE_FROM) dark *= (DARK_RANGE - distance) / (DARK_RANGE - DARK_FADE_FROM);
            if (dark > best) {
                best = (float) dark;
                darkCentre = run.battleCentre();
                darkRun = run;
            }
        }
        return best;
    }

    private static double effectsExtent(Minecraft mc, float partialTick) {
        double furthest = 0;
        for (Run run : RUNS.values()) {
            furthest = Math.max(furthest, BurnsSkyEffects.reach(mc, run, age(run, partialTick), partialTick, darkCentre));
        }
        return furthest;
    }

    private static double battlersExtent(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (darkRun == null || mc.level == null) return 0;
        double furthest = darkCentre.distanceTo(darkRun.payload.targetCentre()) + SHADE_AROUND + 2;
        for (int id : new int[]{darkRun.payload.userId(), darkRun.payload.targetId()}) {
            Entity entity = mc.level.getEntity(id);
            if (entity == null) continue;
            Vec3 middle = entity.getPosition(partialTick).add(0, entity.getBbHeight() / 2, 0);
            double body = Math.sqrt(entity.getBbWidth() * entity.getBbWidth() / 2 + entity.getBbHeight() * entity.getBbHeight() / 4);
            furthest = Math.max(furthest, middle.distanceTo(darkCentre) + body + SHADE_AROUND);
        }
        return furthest;
    }

    private static void drawShade(Minecraft mc, float partialTick) {
        float dark = darkness(partialTick);
        if (dark <= 0.01f) return;
        double fromCentre = viewCamera.distanceTo(darkCentre);
        if (fromCentre + SHADE_BEYOND_CAMERA > SHADE_MAX) return;
        float inner = (float) Math.min(SHADE_MAX - SHADE_EDGE, Math.max(Math.max(Math.max(SHADE_FROM, battlersExtent(partialTick)),
                effectsExtent(mc, partialTick) + SHADE_CLEARANCE), fromCentre + SHADE_BEYOND_CAMERA));
        Vector3f centre = new Vector3f((float) (darkCentre.x - viewCamera.x), (float) (darkCentre.y - viewCamera.y),
                (float) (darkCentre.z - viewCamera.z));
        VertexConsumer out = PASS.begin(WarpRenderTypes.SHADE, VIEW);
        for (int shell = SHADE_SHELLS - 1; shell >= 0; shell--) {
            float radius = inner + SHADE_EDGE * shell / (SHADE_SHELLS - 1f);
            float alpha = shell == SHADE_SHELLS - 1 ? dark : dark * 0.25f;
            Domes.sphere(out, centre, radius, 0f, 0f, 0f, alpha);
        }
    }

    private static void renderWorld(WorldRenderContext context) {
        viewCamera = context.camera().getPosition();
        if (RUNS.isEmpty()) return;
        VIEW.set(context.positionMatrix());
        PROJECTION.set(context.projectionMatrix());

        Minecraft mc = Minecraft.getInstance();
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
        for (Run run : RUNS.values()) {
            if (run.release == null && age(run, partialTick) >= BurnsSkyTimeline.THROW) {
                run.release = run.held(mc, BurnsSkyTimeline.THROW, partialTick);
            }
        }
        drawShade(mc, partialTick);
        BurnsSkyEffects effects = new BurnsSkyEffects(PASS, VIEW, PROJECTION, viewCamera);
        for (Run run : RUNS.values()) {
            if (viewCamera.distanceTo(run.battleCentre()) > DARK_RANGE) continue;
            effects.draw(mc, run, age(run, partialTick), partialTick);
        }
        PASS.draw();
    }

    private static void renderHud(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || RUNS.isEmpty() || mc.options.hideGui) return;
        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        float best = 0;
        int colour = 0xFFFFFF;
        for (Run run : RUNS.values()) {
            if (viewCamera.distanceTo(run.battleCentre()) > DARK_RANGE) continue;
            double age = age(run, partialTick);
            for (int[] fill : BurnsSkyTimeline.FILLS) {
                if (age < fill[0] || age > fill[3]) continue;
                double amount = age < fill[1] ? (age - fill[0]) / Math.max(1, fill[1] - fill[0])
                        : age < fill[2] ? 1 : 1 - (age - fill[2]) / Math.max(1, fill[3] - fill[2]);
                float strength = (float) (Mth.clamp(amount, 0, 1) * fill[7] / 100.0);
                if (strength > best) {
                    best = strength;
                    colour = fill[4] << 16 | fill[5] << 8 | fill[6];
                }
            }
        }
        if (best <= 0.01f) return;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ((int) (best * 255) << 24) | colour);
    }
}
