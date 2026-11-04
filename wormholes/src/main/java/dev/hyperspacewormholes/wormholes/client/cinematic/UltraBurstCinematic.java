package dev.hyperspacewormholes.wormholes.client.cinematic;

import com.cobblemon.mod.common.client.render.MatrixWrapper;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.client.UltraWorldMusic;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Plays the Ultra Burst cutscene from Ultra Sun and Ultra Moon in the world: the game's camera path frame by frame
 * (CinematicScene ultra_burst_dusk or _dawn, 30 frames a second, so 1.5 a tick) with Necrozma held at the arena centre,
 * animated with the game's motions retargeted onto Mega Showdown's models. Shots are pulled in or lifted when blocks
 * are in the way, the HUD and other Pokemon are hidden, and the core leaves the head through a cutscene-only aspect
 * (CORE_ASPECT).
 */
public final class UltraBurstCinematic {
    private static final float FRAMES_PER_TICK = 1.5f;
    private static final int FADE_BACK = 12, LIFT_STEP = 3, HIDE_FROM = 300, FLASH_FROM = 284, FLASH_TO = 400;
    private static final int HIDE_PLAYER_FROM = 454, HIDE_PLAYER_TO = 568;
    private static final double MAX_LIFT = 12, CLOSE_UP = 14, ULTRA_FROM = 300, OPENING_TO = 97;
    private static final double DOME_VIEW = 1.45, INSIDE_SHOT = 173, MIN_KEEP = 0.2;
    private static final double[] OPENING_BACK = {2.4, 4.4}, OPENING_UP = {2.4, 2.7};
    private static final double[] DUSK_DOME = {0.24, 2.38, 4.6}, DAWN_DOME = {-0.32, 3.08, 7.45};
    private static final double[] DUSK_HEAD = {2.0, 3.27}, DAWN_HEAD = {-0.1, 4.65};
    private static final double[][] FLOW = {{370, -2.6}, {480, -1.8}, {580, 8.0}, {698, 1.5}};
    private static final double[][] DUSK_LOOK_UP = {{528, 0}, {568, 13.5}}, DAWN_LOOK_UP = {{536, 0}, {568, 7.2}};
    private static @Nullable CinematicScene scene;
    private static int entityId = -1, age = -1, fadeAge = -1;
    private static boolean registered, closeUp, dawnScene;
    private static Vec3 boss = Vec3.ZERO, player = Vec3.ZERO;
    private static float playerYaw, bossHeight = 4, sizeFactor = 1, facing;
    private static double domeBack, domeUp, domeRadius = 4, headForward, headUp = 3;
    private static @Nullable Vec3 domeTracked, coreTracked;
    private static Vec3 coreAxis = Vec3.ZERO;
    private static @Nullable CameraType previousView;
    private static final SoundEvent MUSIC = SoundEvent.createVariableRangeEvent(Wormholes.id("music.ultra_burst"));
    private static @Nullable SoundInstance music;
    private static final Map<Integer, Double> LIFTS = new HashMap<>(), PUSHES = new HashMap<>(), PULLS = new HashMap<>();
    private static double cachedFrame = -1;
    private static @Nullable double[] cached;
    private static final Fx.Motion MOTION = new Fx.Motion();
    private static final String CORE_ASPECT = "ultra-burst-core";
    private static boolean labelWasHidden;

    private UltraBurstCinematic() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        WorldRenderEvents.LAST.register(UltraBurstEffects::collect);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop());
    }

    public static boolean play(int bossId, int challengerId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(bossId) instanceof PokemonEntity necrozma)) return false;
        boolean dawn = necrozma.getPokemon().getAspects().contains("dawn-fusion");
        CinematicScene loaded = CinematicScene.load(dawn ? "ultra_burst_dawn" : "ultra_burst_dusk");
        if (loaded == null) return false;
        register();
        MOTION.stop(entityId);
        scene = loaded;
        dawnScene = dawn;
        entityId = bossId;
        boss = necrozma.position();
        bossHeight = necrozma.getBbHeight();
        sizeFactor = Mth.clamp(necrozma.getPokemon().getScaleModifier(), 0.25f, 4f);
        labelWasHidden = necrozma.getEntityData().get(PokemonEntity.getHIDE_LABEL());
        double[] dome = dawn ? DAWN_DOME : DUSK_DOME;
        domeBack = dome[0] * sizeFactor;
        domeUp = dome[1] * sizeFactor;
        domeRadius = dome[2] * sizeFactor;
        domeTracked = null;
        coreTracked = null;
        coreAxis = Vec3.ZERO;
        double[] head = dawn ? DAWN_HEAD : DUSK_HEAD;
        headForward = head[0] * sizeFactor;
        headUp = head[1] * sizeFactor;
        Entity challenger = mc.level.getEntity(challengerId);
        if (!(challenger instanceof Player)) challenger = nearestPlayer(boss);
        player = challenger != null ? challenger.position() : boss.add(0, 0, -loaded.playerDistance);
        playerYaw = (float) (Mth.atan2(-(boss.x - player.x), boss.z - player.z) * Mth.RAD_TO_DEG);
        if (previousView == null) previousView = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        LIFTS.clear();
        PUSHES.clear();
        PULLS.clear();
        cachedFrame = -1;
        age = 0;
        fadeAge = -1;
        facing = facingAt(0);
        moveNecrozma(boss, boss, facing, facing);
        UltraBurstEffects.start(dawn);
        mc.getMusicManager().stopPlaying();
        mc.getSoundManager().stop(null, SoundSource.MUSIC);
        if (music != null) mc.getSoundManager().stop(music);
        music = new SimpleSoundInstance(MUSIC.getLocation(), SoundSource.MASTER, 1f, 1f, SoundInstance.createUnseededRandom(),
                false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        mc.getSoundManager().play(music);
        UltraWorldMusic.holdReactiveMusic(music);
        return true;
    }

    private static void turn(Entity entity, float from, float to) {
        entity.yRotO = from;
        entity.setYRot(to);
        entity.setXRot(0);
        entity.xRotO = 0;
        if (entity instanceof LivingEntity living) {
            living.yBodyRotO = from;
            living.yHeadRotO = from;
            living.setYBodyRot(to);
            living.setYHeadRot(to);
        }
    }

    private static @Nullable Entity nearestPlayer(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        Entity nearest = mc.player;
        double best = Double.MAX_VALUE;
        for (Player p : mc.level.players()) {
            if (p.isSpectator()) continue;
            double d = p.distanceToSqr(at);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        return nearest;
    }

    private static float facingAt(double frame) {
        if (scene == null || frame < HIDE_FROM) return playerYaw + 180;
        return playerYaw + scene.sample(frame)[7];
    }

    public static Vec3 domeCentre() {
        if (domeTracked != null) return domeTracked;
        double r = facing * Mth.DEG_TO_RAD;
        return boss.add(Math.sin(r) * domeBack, domeUp, -Math.cos(r) * domeBack);
    }

    public static void trackDome(Entity entity, PosableModel model, PosableState state) {
        if (!isBoss(entity) || frame(0) >= HIDE_FROM) return;
        MatrixWrapper root = state.getLocatorStates().get("root");
        if (root == null) return;
        PoseStack pose = new PoseStack();
        pose.last().pose().set(root.getMatrix()).rotateZ(-180f * Mth.DEG_TO_RAD);
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        float[] shaft = new float[7];
        Vector3f corner = new Vector3f();
        for (Map.Entry<String, Bone> top : model.getRootPart().getChildren().entrySet()) {
            if (!(top.getValue() instanceof ModelPart part)) continue;
            boolean core = top.getKey().startsWith("core");
            part.visit(pose, (at, path, index, cube) -> {
                if (core || path.contains("/core")) {
                    if (cube.maxY - cube.minY <= shaft[6]) return;
                    shaft[6] = cube.maxY - cube.minY;
                    float x = (cube.minX + cube.maxX) / 32f, z = (cube.minZ + cube.maxZ) / 32f;
                    at.pose().transformPosition(x, cube.minY / 16f, z, corner);
                    shaft[0] = corner.x;
                    shaft[1] = corner.y;
                    shaft[2] = corner.z;
                    at.pose().transformPosition(x, cube.maxY / 16f, z, corner);
                    shaft[3] = corner.x;
                    shaft[4] = corner.y;
                    shaft[5] = corner.z;
                    return;
                }
                for (int i = 0; i < 8; i++) {
                    at.pose().transformPosition(((i & 1) == 0 ? cube.minX : cube.maxX) / 16f,
                            ((i & 2) == 0 ? cube.minY : cube.maxY) / 16f, ((i & 4) == 0 ? cube.minZ : cube.maxZ) / 16f, corner);
                    box[0] = Math.min(box[0], corner.x);
                    box[1] = Math.min(box[1], corner.y);
                    box[2] = Math.min(box[2], corner.z);
                    box[3] = Math.max(box[3], corner.x);
                    box[4] = Math.max(box[4], corner.y);
                    box[5] = Math.max(box[5], corner.z);
                }
            });
        }
        Vec3 at = entity.getPosition(state.getPartialTicks());
        if (shaft[6] > 0) {
            coreTracked = at.add((shaft[0] + shaft[3]) / 2, (shaft[1] + shaft[4]) / 2, (shaft[2] + shaft[5]) / 2);
            coreAxis = new Vec3(shaft[3] - shaft[0], shaft[4] - shaft[1], shaft[5] - shaft[2]);
        }
        if (box[0] > box[3]) return;
        domeTracked = at.add((box[0] + box[3]) / 2, (box[1] + box[4]) / 2, (box[2] + box[5]) / 2);
    }

    public static @Nullable Vec3 coreCentre() {
        return coreTracked;
    }

    public static Vec3 coreAxis() {
        return coreAxis;
    }

    public static double domeRadius() {
        return domeRadius;
    }

    public static Vec3 forward() {
        double r = facing * Mth.DEG_TO_RAD;
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    public static Vec3 headTop() {
        return boss.add(forward().scale(headForward)).add(0, headUp, 0);
    }

    public static float sizeFactor() {
        return sizeFactor;
    }

    private static void moveNecrozma(Vec3 from, Vec3 to, float fromYaw, float toYaw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(entityId) instanceof PokemonEntity necrozma)) return;
        necrozma.setPos(to);
        necrozma.xo = from.x;
        necrozma.yo = from.y;
        necrozma.zo = from.z;
        turn(necrozma, fromYaw, toYaw);
    }

    public static boolean isPlaying() {
        return scene != null;
    }

    public static boolean isShowing() {
        return scene != null || fadeAge >= 0;
    }

    public static boolean hides(Entity entity) {
        if (scene == null) return false;
        double f = frame(0);
        if (entity instanceof Player) return closeUp || f >= HIDE_PLAYER_FROM && f < HIDE_PLAYER_TO;
        if (!(entity instanceof PokemonEntity)) return false;
        if (entity.getId() != entityId) return true;
        return f >= FLASH_FROM && f < FLASH_TO;
    }

    public static double frame(float partialTick) {
        return (age + partialTick) * FRAMES_PER_TICK;
    }

    public static Vec3 boss() {
        return boss;
    }

    public static float bossHeight() {
        return bossHeight;
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
        age++;
        if (mc.level.getEntity(entityId) instanceof PokemonEntity necrozma) bossHeight = necrozma.getBbHeight();
        CinematicScene s = scene;
        boolean cut = s.shot(frame(1)) != s.shot(frame(0));
        float now = facingAt(frame(0)), next = facingAt(frame(1));
        double from = flow(s, frame(0)) * sizeFactor, to = flow(s, frame(1)) * sizeFactor;
        moveNecrozma(boss.add(0, cut ? to : from, 0), boss.add(0, to, 0), cut ? next : now, next);
        facing = now;
        if (mc.level.getEntity(entityId) instanceof PokemonEntity necrozma) {
            necrozma.getEntityData().set(PokemonEntity.getHIDE_LABEL(), true);
            core(necrozma, frame(0) < HIDE_FROM);
            animate(necrozma, frame(0));
        }
        UltraBurstEffects.tick(frame(0), boss, bossHeight);
        if (frame(0) > s.frames) {
            finish();
            fadeAge = 0;
        }
    }

    private static void animate(PokemonEntity necrozma, double frame) {
        if (!(necrozma.getDelegate() instanceof PosableState state)) return;
        Set<String> aspects = necrozma.getPokemon().getAspects();
        String name = aspects.contains("ultra-fusion") ? dawnScene ? "ultra_dawn" : "ultra"
                : aspects.contains("dusk-fusion") ? "dusk" : aspects.contains("dawn-fusion") ? "dawn" : null;
        if (name == null) return;
        float into = (float) ((frame - (name.startsWith("ultra") ? ULTRA_FROM : 0)) / 30);
        float clock = state.getAnimationSeconds() - state.getPartialTicks() / 20f;
        if (MOTION.playing(state, name)) {
            MOTION.hold(clock - into);
            return;
        }
        BedrockAnimation animation = Fx.Motion.find(name);
        if (animation == null || into < 0 || into >= animation.getAnimationLength()) return;
        MOTION.start(state, animation, name, clock - into);
    }

    private static void core(PokemonEntity necrozma, boolean on) {
        Set<String> aspects = necrozma.getEntityData().get(PokemonEntity.getASPECTS());
        boolean want = on && (aspects.contains("dusk-fusion") || aspects.contains("dawn-fusion"));
        if (want == aspects.contains(CORE_ASPECT)) return;
        Set<String> next = new HashSet<>(aspects);
        if (want) next.add(CORE_ASPECT);
        else next.remove(CORE_ASPECT);
        necrozma.getEntityData().set(PokemonEntity.getASPECTS(), next);
    }

    public static boolean isBoss(Entity entity) {
        return scene != null && entity.getId() == entityId;
    }

    private static void finish() {
        Minecraft mc = Minecraft.getInstance();
        MOTION.stop(entityId);
        if (mc.level != null && mc.level.getEntity(entityId) instanceof PokemonEntity necrozma) {
            necrozma.getEntityData().set(PokemonEntity.getHIDE_LABEL(), labelWasHidden);
            core(necrozma, false);
        }
        if (previousView != null) mc.options.setCameraType(previousView);
        previousView = null;
        scene = null;
        age = -1;
        closeUp = false;
        moveNecrozma(boss, boss, playerYaw + 180, playerYaw + 180);
        if (music != null) mc.getSoundManager().stop(music);
        music = null;
    }

    public static void stop() {
        if (scene != null) finish();
        fadeAge = -1;
    }

    private record View(Vec3 pos, float yaw, float pitch, Vec3 subject) {}

    private static View raw(CinematicScene s, double frame) {
        float[] at = s.sample(frame);
        boolean onPlayer = s.onPlayer(frame);
        if (onPlayer && frame < OPENING_TO) return opening(frame);
        Vec3 base = onPlayer ? player : boss;
        float baseYaw = onPlayer ? playerYaw : facingAt(frame);
        double scale = s.scale(frame) * (onPlayer ? 1 : sizeFactor);
        double r = baseYaw * Mth.DEG_TO_RAD, cos = Math.cos(r), sin = Math.sin(r);
        double lx = at[0] * scale, ly = at[1] * scale, lz = at[2] * scale;
        Vec3 pos = base.add(lx * cos - lz * sin, ly, lx * sin + lz * cos);
        int shot = s.shot(frame);
        if (!onPlayer && frame < HIDE_FROM && s.shotStart(shot) != INSIDE_SHOT) {
            Vec3 centre = base.add(sin * domeBack, 0, -cos * domeBack);
            double push = PUSHES.computeIfAbsent(shot, k -> outward(s, s.shotStart(k)));
            pos = centre.add(pos.subtract(centre).scale(push));
        }
        double off = (flow(s, frame) - s.hover(frame)) * sizeFactor;
        float pitch = at[4];
        if (!onPlayer) {
            pos = pos.add(0, off, 0);
        } else if (off != 0) {
            Vec3 game = boss.add(0, bossHeight * 0.5 + s.hover(frame) * sizeFactor, 0);
            pitch += pitchTo(pos, game.add(0, off, 0)) - pitchTo(pos, game);
        }
        if (onPlayer && shot == s.shot(HIDE_PLAYER_FROM)) pitch -= (float) eased(dawnScene ? DAWN_LOOK_UP : DUSK_LOOK_UP, frame);
        Vec3 subject = onPlayer ? player.add(0, 1.2, 0) : base.add(0, bossHeight * 0.5 + flow(s, frame) * sizeFactor, 0);
        return new View(pos, baseYaw + at[3], pitch, subject);
    }

    private static double flow(CinematicScene s, double frame) {
        return frame < FLOW[0][0] ? s.hover(frame) : eased(FLOW, frame);
    }

    private static double eased(double[][] keys, double frame) {
        if (frame <= keys[0][0]) return keys[0][1];
        for (int i = 1; i < keys.length; i++) {
            if (frame > keys[i][0]) continue;
            double t = (frame - keys[i - 1][0]) / (keys[i][0] - keys[i - 1][0]);
            return Mth.lerp(t * t * (3 - 2 * t), keys[i - 1][1], keys[i][1]);
        }
        return keys[keys.length - 1][1];
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        return (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
    }

    private static float pitchTo(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        return (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
    }

    private static double outward(CinematicScene s, int frame) {
        float[] at = s.sample(frame);
        double scale = s.scale(frame) * sizeFactor, lx = at[0] * scale, lz = at[2] * scale;
        double r = facingAt(frame) * Mth.DEG_TO_RAD;
        double x = lx * Math.cos(r) - lz * Math.sin(r) - Math.sin(r) * domeBack;
        double z = lx * Math.sin(r) + lz * Math.cos(r) + Math.cos(r) * domeBack;
        double away = Math.sqrt(x * x + at[1] * scale * at[1] * scale + z * z);
        return away < 1e-3 ? 1 : Math.max(1, DOME_VIEW * domeRadius / away);
    }

    private static View opening(double frame) {
        double t = Mth.clamp(frame / OPENING_TO, 0, 1);
        t = t * t * (3 - 2 * t);
        Vec3 ahead = new Vec3(boss.x - player.x, 0, boss.z - player.z);
        ahead = ahead.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : ahead.normalize();
        Vec3 pos = player.subtract(ahead.scale(Mth.lerp(t, OPENING_BACK[0], OPENING_BACK[1])))
                .add(0, Mth.lerp(t, OPENING_UP[0], OPENING_UP[1]), 0);
        Vec3 at = boss.add(0, domeRadius * 0.12, 0);
        return new View(pos, yawTo(pos, at), pitchTo(pos, at), player.add(0, 1.2, 0));
    }

    private static double liftFor(CinematicScene s, int shot) {
        return LIFTS.computeIfAbsent(shot, k -> {
            int end = s.shotEnd(k);
            double lift = liftAt(s, end - 1);
            for (int f = s.shotStart(k); f < end; f += LIFT_STEP) lift = Math.max(lift, liftAt(s, f));
            return lift;
        });
    }

    private static View placed(CinematicScene s, double frame) {
        View v = raw(s, frame);
        if (!s.onPlayer(frame)) return v;
        double keep = pullFor(s, s.shot(frame));
        if (keep >= 1) return v;
        return new View(v.subject.add(v.pos.subtract(v.subject).scale(keep)), v.yaw, v.pitch, v.subject);
    }

    private static double pullFor(CinematicScene s, int shot) {
        return PULLS.computeIfAbsent(shot, k -> {
            int end = s.shotEnd(k);
            double keep = pullAt(s, end - 1);
            for (int f = s.shotStart(k); f < end; f += LIFT_STEP) keep = Math.min(keep, pullAt(s, f));
            return keep;
        });
    }

    private static double pullAt(CinematicScene s, int frame) {
        View v = raw(s, frame);
        return Fx.keep(v.pos, v.subject, MIN_KEEP);
    }

    private static double liftAt(CinematicScene s, int frame) {
        View v = placed(s, frame);
        double need = 0;
        while (need < MAX_LIFT && (Fx.blocked(v.pos.add(0, need, 0)) || !Fx.sees(v.pos.add(0, need, 0), v.subject))) need += 0.25;
        return need;
    }

    public static @Nullable double[] camera(float partialTick) {
        CinematicScene s = scene;
        if (s == null || Minecraft.getInstance().level == null) return null;
        double frame = frame(partialTick);
        if (frame == cachedFrame && cached != null) return cached;
        float[] at = s.sample(frame);
        View v = placed(s, frame);
        Vec3 pos = v.pos;
        float yaw = v.yaw, pitch = v.pitch;
        double lift = liftFor(s, s.shot(frame));
        if (lift > 0) {
            double reach = Math.max(1, pos.distanceTo(v.subject));
            float yr = yaw * Mth.DEG_TO_RAD, pr = pitch * Mth.DEG_TO_RAD;
            Vec3 look = pos.add(-Mth.sin(yr) * Mth.cos(pr) * reach, -Mth.sin(pr) * reach, Mth.cos(yr) * Mth.cos(pr) * reach);
            pos = pos.add(0, lift, 0);
            yaw = yawTo(pos, look);
            pitch = pitchTo(pos, look);
        }
        closeUp = !s.onPlayer(frame) && pos.distanceTo(v.subject) < CLOSE_UP;
        cached = new double[]{pos.x, pos.y, pos.z, yaw, pitch, at[5], at[6]};
        cachedFrame = frame;
        return cached;
    }

    public static boolean hud(GuiGraphics g, DeltaTracker delta) {
        if (!isShowing()) return false;
        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        if (scene == null) Fx.fadeBack(g, fadeAge, partialTick, FADE_BACK);
        else UltraBurstEffects.screen(g, frame(partialTick), g.guiWidth(), g.guiHeight());
        return true;
    }
}
