package dev.hyperspacewormholes.wormholes.client.cinematic;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimation;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.client.NecrozmaWeakened;
import dev.hyperspacewormholes.wormholes.network.NecrozmaScenePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Plays Ultra Necrozma's two boss-fight cutscenes from a NecrozmaScenePayload: POWER_UP (the start of the battle) and
 * DEFEAT (the Megalo Tower scene after it is beaten), baked cinematics at 30 frames a second with the game's motion
 * retargeted onto Mega Showdown's Ultra model. In the defeat the golden-to-black ball hides the change to base Necrozma
 * at DEFEAT_FORM, and the clock waits there until the server has made it. The sink values lower only the drawn model
 * into the floor (NecrozmaSinkMixin); the hitbox and locators stay with the entity.
 */
public final class NecrozmaSceneCinematic {
    public static final float FRAMES_PER_TICK = 1.5f;
    public static final int POWER_UP_END = 409, DEFEAT_END = 410, DEFEAT_FORM = 297, DEFEAT_WAIT = 120, MEGA_FROM = 162;
    public static final int POWER_UP_TICKS = (int) Math.ceil(POWER_UP_END / FRAMES_PER_TICK) + 1;
    public static final int DEFEAT_TICKS = (int) Math.ceil(DEFEAT_END / FRAMES_PER_TICK) + 1;
    public static final int DEFEAT_FORM_TICK = (int) (DEFEAT_FORM / FRAMES_PER_TICK), MEGA_FROM_TICK = (int) (MEGA_FROM / FRAMES_PER_TICK);
    private static final int FADE_BACK = 12, CHECK_STEP = 6, KEEP_SPREAD = 3, FALL_FROM = 56, FALL_TO = 146, FLOOR_SEARCH = 6;
    private static final int LAND_FROM = 84, LAND_TO = 100;
    private static final double CLOSE_UP = 14, MIN_KEEP = 0.15, BODY = 89 / 16.0, MAX_FOV = 100, REBASE = 0.5;
    private static final double SINK = 4.1, FALL_SINK = 2.4, BASE_SINK = 0;
    private static final float HOLD_SLACK = 0.1f;
    private static long sinkLogged = Long.MIN_VALUE;
    private static @Nullable CinematicScene scene;
    private static int kind = -1, entityId = -1, fadeAge = -1, end;
    private static double clock, lastClock, waited;
    private static boolean registered, closeUp, labelWasHidden, ultraGone, anchored;
    private static Vec3 base = Vec3.ZERO;
    private static float baseYaw, sizeFactor = 1;
    private static @Nullable CameraType previousView;
    private static final Fx.Motion MOTION = new Fx.Motion();
    private static final Map<Integer, Double> KEEPS = new HashMap<>();
    private static double cachedFrame = -1;
    private static @Nullable double[] cached;

    private NecrozmaSceneCinematic() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        WorldRenderEvents.LAST.register(context -> {
            if (scene == null || kind != NecrozmaScenePayload.POWER_UP) return;
            PowerUpEffects.collect(context, frame(context.tickCounter().getGameTimeDeltaPartialTick(false)));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop());
    }

    public static boolean play(NecrozmaScenePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(payload.entityId()) instanceof PokemonEntity necrozma)) return false;
        boolean powerUp = payload.kind() == NecrozmaScenePayload.POWER_UP;
        CinematicScene loaded = CinematicScene.load(powerUp ? "necrozma_power_up" : "necrozma_defeat");
        if (loaded == null) return false;
        register();
        UltraBurstCinematic.stop();
        if (scene != null) finish();
        scene = loaded;
        kind = payload.kind();
        entityId = payload.entityId();
        NecrozmaWeakened.release(entityId);
        if (powerUp) UltraAura.powered(entityId);
        else UltraAura.release(entityId);
        end = powerUp ? POWER_UP_END : DEFEAT_END;
        Vec3 at = payload.at() != null ? payload.at() : necrozma.position();
        base = powerUp ? at : onFloor(at);
        baseYaw = payload.at() != null ? payload.yaw() : necrozma.yBodyRot;
        sizeFactor = Mth.clamp(necrozma.getPokemon().getScaleModifier(), 0.25f, 4f);
        labelWasHidden = necrozma.getEntityData().get(PokemonEntity.getHIDE_LABEL());
        ultraGone = false;
        anchored = payload.at() != null;
        clock = lastClock = waited = 0;
        fadeAge = -1;
        KEEPS.clear();
        cachedFrame = -1;
        if (previousView == null) previousView = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (powerUp) PowerUpEffects.start(sizeFactor);
        else DefeatEffects.start(necrozma, sizeFactor);
        place(necrozma, 0, 0);
        return true;
    }

    static boolean seek(double frame) {
        if (scene == null) return false;
        clock = lastClock = Math.min(frame, end);
        return true;
    }

    public static boolean isPlaying() {
        return scene != null;
    }

    public static boolean isShowing() {
        return scene != null || fadeAge >= 0;
    }

    public static boolean isBoss(Entity entity) {
        return scene != null && entity.getId() == entityId;
    }

    public static int kind() {
        return kind;
    }

    public static double frame(float partialTick) {
        return Math.min(lastClock + (clock - lastClock) * partialTick, end);
    }

    static Vec3 base() {
        return base;
    }

    static boolean ultraGone() {
        return ultraGone;
    }

    static double hover(double frame) {
        return scene == null ? 0 : scene.hover(frame) * sizeFactor;
    }

    static Vec3 body(double frame) {
        double lift = kind == NecrozmaScenePayload.DEFEAT && !ultraGone ? hover(frame) : 0;
        double up = kind == NecrozmaScenePayload.POWER_UP ? BODY * sizeFactor : Math.max(0.6, (BODY * sizeFactor + lift) * 0.85);
        return base.add(0, up, 0);
    }

    private record Sunk(double floor, double depth, boolean ultra, double amount) {}

    private static @Nullable Sunk sunk(Entity entity, float partialTick) {
        boolean boss = scene != null && kind == NecrozmaScenePayload.DEFEAT && entity.getId() == entityId;
        Double held = NecrozmaWeakened.downFloor(entity.getId()), lying = NecrozmaWeakened.weakFloor(entity.getId());
        if (boss && ultraGone) return new Sunk(base.y, BASE_SINK, false, 1);
        double f = boss ? frame(partialTick) : 0;
        if (boss && f >= LAND_FROM) {
            double t = Mth.clamp((f - LAND_FROM) / (LAND_TO - LAND_FROM), 0, 1);
            return new Sunk(base.y, f >= FALL_TO ? SINK : FALL_SINK, true, t * t * (3 - 2 * t));
        }
        if (held != null) return new Sunk(held, SINK, true, 1);
        if (lying != null) return new Sunk(lying, BASE_SINK, false, 1);
        return null;
    }

    private static double size(Entity entity) {
        return entity instanceof PokemonEntity p ? Mth.clamp(p.getPokemon().getScaleModifier(), 0.25f, 4f) : 1;
    }

    public static double sink(Entity entity, float partialTick) {
        Sunk sunk = sunk(entity, partialTick);
        if (sunk == null) return 0;
        double size = size(entity);
        double drawn = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double offset = sunk.ultra ? (sunk.floor - sunk.depth * size - drawn) * sunk.amount
                : Math.min(0, sunk.floor - sunk.depth * sunk.amount * size - drawn);
        long second = entity.level().getGameTime() / 20;
        if (second != sinkLogged) {
            sinkLogged = second;
            Wormholes.LOGGER.info("[necrozma] defeat sink ({}): entity drawn {} blocks over the floor, render offset {} "
                    + "(to {} under)", sunk.ultra ? "ultra" : "base",
                    String.format("%.2f", drawn - sunk.floor), String.format("%.2f", offset),
                    String.format("%.2f", sunk.depth * sunk.amount * size));
        }
        return offset;
    }

    public static boolean hides(Entity entity) {
        if (scene == null) return false;
        if (entity instanceof Player) {
            double f = frame(0);
            return kind == NecrozmaScenePayload.POWER_UP || closeUp || f >= FALL_FROM && f < FALL_TO;
        }
        return entity instanceof PokemonEntity && entity.getId() != entityId;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            stop();
            return;
        }
        if (mc.isPaused()) return;
        if (fadeAge >= 0 && ++fadeAge > FADE_BACK) fadeAge = -1;
        if (scene == null) return;
        PokemonEntity necrozma = mc.level.getEntity(entityId) instanceof PokemonEntity p ? p : null;
        if (necrozma != null && kind == NecrozmaScenePayload.DEFEAT && !ultraGone
                && !necrozma.getEntityData().get(PokemonEntity.getASPECTS()).contains("ultra-fusion")) {
            ultraGone = true;
            MOTION.stop(entityId);
            NecrozmaWeakened.start(necrozma, baseYaw, base.y);
            DefeatEffects.formChanged();
        }
        if (necrozma != null && kind == NecrozmaScenePayload.DEFEAT && !anchored) rebase(necrozma);
        lastClock = clock;
        boolean waiting = kind == NecrozmaScenePayload.DEFEAT && !ultraGone && clock >= DEFEAT_FORM && waited < DEFEAT_WAIT;
        if (waiting) waited += FRAMES_PER_TICK;
        else clock = Math.min(clock + FRAMES_PER_TICK, end + 1);
        if (necrozma != null) {
            necrozma.getEntityData().set(PokemonEntity.getHIDE_LABEL(), true);
            place(necrozma, lastClock, clock);
            if (!ultraGone) animate(necrozma, lastClock);
        }
        if (kind == NecrozmaScenePayload.POWER_UP) PowerUpEffects.tick(lastClock);
        else DefeatEffects.tick(lastClock, necrozma);
        if (clock > end) {
            finish();
            fadeAge = 0;
        }
    }

    private static void rebase(PokemonEntity necrozma) {
        // All-zero deltas decode to the codec's base, the last position the server sent.
        Vec3 synced = necrozma.getPositionCodec().decode(0, 0, 0);
        if (Math.abs(synced.x - base.x) < REBASE && Math.abs(synced.z - base.z) < REBASE && Math.abs(synced.y - base.y) < 2 * REBASE) return;
        Vec3 moved = onFloor(synced);
        if (moved.distanceTo(base) < REBASE) return;
        base = moved;
        baseYaw = necrozma.lerpTargetYRot();
        KEEPS.clear();
        cachedFrame = -1;
    }

    private static Vec3 onFloor(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return at;
        BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy <= FLOOR_SEARCH; dy++) {
            block.set(Mth.floor(at.x), Mth.floor(at.y + 0.5) - dy, Mth.floor(at.z));
            var shape = mc.level.getBlockState(block).getCollisionShape(mc.level, block);
            if (shape.isEmpty()) continue;
            double top = block.getY() + shape.max(Direction.Axis.Y);
            return top <= at.y + 0.5 ? new Vec3(at.x, top, at.z) : at;
        }
        return at;
    }

    private static void place(PokemonEntity necrozma, double from, double to) {
        if (kind != NecrozmaScenePayload.DEFEAT || ultraGone) {
            if (kind == NecrozmaScenePayload.DEFEAT) move(necrozma, base, base);
            return;
        }
        boolean cut = scene != null && scene.shot(to) != scene.shot(from);
        Vec3 a = base.add(0, hover(cut ? to : from), 0), b = base.add(0, hover(to), 0);
        move(necrozma, a, b);
    }

    private static void move(LivingEntity entity, Vec3 from, Vec3 to) {
        entity.setPos(to);
        entity.xo = from.x;
        entity.yo = from.y;
        entity.zo = from.z;
        entity.setDeltaMovement(Vec3.ZERO);
        entity.yBodyRotO = baseYaw;
        entity.setYBodyRot(baseYaw);
        entity.yHeadRotO = baseYaw;
        entity.setYHeadRot(baseYaw);
        entity.yRotO = baseYaw;
        entity.setYRot(baseYaw);
    }

    private static void animate(PokemonEntity necrozma, double frame) {
        if (!(necrozma.getDelegate() instanceof PosableState state)) return;
        String name = kind == NecrozmaScenePayload.POWER_UP ? "power_up" : "defeat";
        float into = (float) (frame / 30);
        float clockSeconds = state.getAnimationSeconds() - state.getPartialTicks() / 20f;
        if (MOTION.playing(state, name)) {
            MOTION.hold(clockSeconds - Math.min(into, MOTION.length() - HOLD_SLACK));
            return;
        }
        BedrockAnimation animation = Fx.Motion.find(name);
        if (animation == null) return;
        MOTION.start(state, animation, name, clockSeconds - Math.min(into, (float) animation.getAnimationLength() - HOLD_SLACK));
    }

    private static void finish() {
        Minecraft mc = Minecraft.getInstance();
        MOTION.stop(entityId);
        if (mc.level != null && mc.level.getEntity(entityId) instanceof PokemonEntity necrozma) {
            necrozma.getEntityData().set(PokemonEntity.getHIDE_LABEL(), labelWasHidden);
            if (kind == NecrozmaScenePayload.DEFEAT && !ultraGone) {
                Vec3 down = base.add(0, hover(end), 0);
                move(necrozma, down, down);
                NecrozmaWeakened.keepDown(necrozma, down, baseYaw, base.y);
            } else if (kind == NecrozmaScenePayload.DEFEAT) {
                move(necrozma, base, base);
            }
        }
        if (kind == NecrozmaScenePayload.DEFEAT) {
            DefeatEffects.stop();
            Wormholes.LOGGER.info("[necrozma] defeat scene over on this client, camera handed back ({})",
                    ultraGone ? "base form" : "still Ultra Necrozma, held down");
        }
        if (previousView != null) mc.options.setCameraType(previousView);
        previousView = null;
        scene = null;
        closeUp = false;
    }

    public static void stop() {
        if (scene != null) finish();
        fadeAge = -1;
    }

    private record View(Vec3 pos, float yaw, float pitch, float roll, float fov, Vec3 subject) {}

    private static View raw(CinematicScene s, double frame) {
        boolean powerUp = kind == NecrozmaScenePayload.POWER_UP;
        float[] at = powerUp ? s.smoothSample(frame) : s.sample(frame);
        double scale = s.scale(frame) * sizeFactor;
        double r = baseYaw * Mth.DEG_TO_RAD, cos = Math.cos(r), sin = Math.sin(r);
        double lx = at[0] * scale, ly = at[1] * scale, lz = at[2] * scale;
        Vec3 pos = base.add(lx * cos - lz * sin, ly, lx * sin + lz * cos);
        if (powerUp) pos = pos.add(0, -(at.length > 10 ? at[10] : 0) * sizeFactor, 0);
        return new View(pos, baseYaw + at[3], at[4], at[5], at[6], body(frame));
    }

    private static double keepFor(CinematicScene s, double frame) {
        int shot = s.shot(frame);
        int from = s.shotStart(shot), to = Math.min(s.shotEnd(shot), end + 1) - 1;
        double at = (frame - from) / CHECK_STEP;
        int i = (int) Math.floor(at);
        double t = at - i, t2 = t * t, t3 = t2 * t;
        double p = spreadKeep(s, from, to, Math.max(i - 1, 0)), a = spreadKeep(s, from, to, i);
        double b = spreadKeep(s, from, to, i + 1), n = spreadKeep(s, from, to, i + 2);
        double keep = 0.5 * (2 * a + (b - p) * t + (2 * p - 5 * a + 4 * b - n) * t2 + (3 * a - p - 3 * b + n) * t3);
        return Mth.clamp(keep, MIN_KEEP, 1);
    }

    private static double spreadKeep(CinematicScene s, int from, int to, int step) {
        double keep = 1;
        for (int j = step - KEEP_SPREAD; j <= step + KEEP_SPREAD; j++) {
            int f = Mth.clamp(from + j * CHECK_STEP, from, to);
            keep = Math.min(keep, KEEPS.computeIfAbsent(f, k -> keepAt(s, k)));
        }
        return keep;
    }

    private static double keepAt(CinematicScene s, int frame) {
        View v = raw(s, frame);
        return Fx.keep(v.pos, v.subject, MIN_KEEP);
    }

    public static @Nullable double[] camera(float partialTick) {
        CinematicScene s = scene;
        if (s == null || Minecraft.getInstance().level == null) return null;
        double frame = frame(partialTick);
        if (frame == cachedFrame && cached != null) return cached;
        View v = raw(s, frame);
        double keep = keepFor(s, frame);
        Vec3 pos = keep >= 1 ? v.pos : v.subject.add(v.pos.subtract(v.subject).scale(keep));
        double fov = keep >= 1 ? v.fov : Math.min(MAX_FOV, 2 * Math.toDegrees(Math.atan(Math.tan(Math.toRadians(v.fov) / 2) / keep)));
        if (kind == NecrozmaScenePayload.POWER_UP) pos = pos.add(PowerUpEffects.shake(frame));
        else pos = pos.add(DefeatEffects.shake(frame));
        closeUp = pos.distanceTo(v.subject) < CLOSE_UP * Math.max(1, sizeFactor);
        cached = new double[]{pos.x, pos.y, pos.z, v.yaw, v.pitch, v.roll, fov};
        cachedFrame = frame;
        return cached;
    }

    public static boolean hud(GuiGraphics g, DeltaTracker delta) {
        if (!isShowing()) return false;
        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        if (scene == null) Fx.fadeBack(g, fadeAge, partialTick, FADE_BACK);
        else if (kind == NecrozmaScenePayload.POWER_UP) PowerUpEffects.screen(g, frame(partialTick), w, h);
        else DefeatEffects.screen(g, frame(partialTick), w, h);
        return true;
    }
}
