package dev.hyperspacewormholes.wormholes.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.client.render.OwnPass;
import dev.hyperspacewormholes.wormholes.client.render.WarpRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import static dev.hyperspacewormholes.wormholes.zmove.BurnsSkyTimeline.*;

/**
 * Draws one frame of the Light That Burns the Sky effects for BurnsSkyClient, on Ultra Sun's timeline
 * (BurnsSkyTimeline) at half a block per game metre.
 * Everything is pixel-textured meshes (stepped shades, flipbooks, sampled nearest) in Minecraft's own effect style,
 * depth tested except the screen washes.
 */
final class BurnsSkyEffects {
    private static final int[] GOLD = {255, 225, 70}, PALE = {255, 255, 153}, LIME = {120, 255, 0}, WHITE = {255, 255, 255};
    private static final int[] BIRTH = {50, 255, 0}, DASHED_GOLD = {255, 240, 120}, TAKE_AWAY = {152, 0, 255};
    private static final int[] PRE_GLOW_GOLD = {255, 200, 60}, PRE_GLOW_ORANGE = {255, 120, 20}, PRE_GLOW_BLUE = {80, 160, 255};
    private static final int[][] RAY_COLOURS = {{255, 220, 60}, {120, 255, 60}, {80, 160, 255}, {255, 90, 30}};
    private static final int[][] MOTE_COLOURS = {{200, 255, 255}, {130, 120, 255}, {255, 240, 170}};
    private static final int[][] TRAIL_COLOURS = {{255, 227, 0}, {255, 140, 0}, {0, 255, 170}};
    private static final int[][] FIRE_COLOURS = {{255, 255, 255}, {255, 245, 200}, {200, 200, 90}, {255, 200, 40}, {140, 80, 30}};
    private static final int[][] RAINBOW = {{254, 233, 65}, {0, 136, 254}, {243, 87, 148}, {120, 255, 200}, {255, 250, 90}};
    private static final int[][] SMOKE_COLOURS = {{255, 200, 110}, {250, 160, 80}, {190, 165, 150}, {150, 145, 140}};
    private static final int[] CLOUD_TOP = {255, 250, 190}, CLOUD = {255, 230, 110};
    private static final int[] CLOUD_FRONT = {240, 228, 200}, CLOUD_BACK = {225, 195, 140};
    private static final int[][] WAVE_DUST = {{255, 240, 190}, {230, 205, 150}, {185, 165, 135}};
    private static final int[][] PILLAR_COLOURS = {{255, 250, 190}, {255, 215, 60}, {255, 140, 30}, {235, 70, 25}};
    private static final double PILLAR_WIDTH = 0.5;
    private static final int DOME_TEX_WIDTH = 16, DOME_TEX_HEIGHT = 8;
    private static final double PIXEL = 0.5;
    private static final double WAVE_WIDTH = 0.35;
    private static final double WAVE_LEAN = 0.18;
    private static final float WAVE_BODY = 0.45f;
    private static final float WAVE_NEAR = 0.3f;
    private static final int WAVE_SEGMENTS = 64;
    private static final int WAVE_REPEATS = 8;
    private static final int EDGE_REPEATS = 48;
    private static final int WAVE_PUFFS = 32;
    private static final float[] WAVE_COS = new float[WAVE_SEGMENTS + 1], WAVE_SIN = new float[WAVE_SEGMENTS + 1];
    private static final int[] FLAME_GLOW = {255, 150, 40}, DOME_TINT = {255, 255, 235};
    private static final double FLAME_HEIGHT = 2.5, FLAME_LEAN = 0.3, FLAME_TILE = 2, FLAME_GLOW_WIDTH = 0.9, RING_WIDTH = 0.6;
    private static final int RING_REPEATS = 24;
    private static final float DOME_CORE = 0.8f;
    private static final float[][] SCREEN_CORNERS = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
    private static final double[] HALO = {1.25, 0.55, 1.9, 0.22};
    private static final double RING_SCALE = 2.2;
    private static final double WAVE_RADIUS = 44;
    private static final double WAVE_HEIGHT = 13;
    private static final double BALL_REACH = 2.2;
    private static final double INSIDE_MARGIN = 0.3;
    private static final double[] SHELL_SIZES = {0.42, 0.62, 0.8, BALL_HALF, BALL_OUTER};
    private static final double[] SHELL_SPINS = {0.01, -0.018, 0.012, -0.007, -0.007};
    private static final float[] SHELL_LIGHT = {1f, 0.9f, 0.55f, 0.3f, 0.12f};
    private static final int[][] SHELL_COLOURS = {{255, 255, 255}, {189, 255, 0}, {112, 240, 19}, {0, 220, 170}, {120, 255, 0}};
    private static final int[] GROWTH_STEPS = {GROW_5, GROW_12_FROM, GROW_20_FROM};
    private static final double PUFF_TICKS = 16;
    private static final int FIRE_WALL_TICKS = 21;

    private record Atlas(ResourceLocation texture, int cols, int rows, int frames) {
        float[] uv(int frame) {
            int f = Mth.clamp(frame, 0, frames - 1);
            float w = 1f / cols, h = 1f / rows;
            return new float[]{(f % cols) * w, (f / cols) * h, (f % cols + 1) * w, (f / cols + 1) * h};
        }
    }

    private record Sprite(Vec3 at, float half, int frame, float[] colour) {}

    private static final Atlas CUBE = atlas("ball_cube", 4, 4, 16);
    private static final Atlas VORTEX = atlas("vortex", 4, 2, 8);
    private static final Atlas CRACK_FRAMES = atlas("cracks", 2, 2, 4);
    private static final Atlas SMOKE_RING = atlas("ring", 2, 2, 4);
    private static final Atlas WAVE_CLOUD = atlas("wave", 1, 4, 4);
    private static final Atlas FLAME_WALL = atlas("flame_wall", 1, 4, 4);
    private static final Atlas DOME_LIGHT = atlas("dome_light", 1, 4, 4);
    private static final Atlas PILLAR_FLAME = atlas("pillar_flame", 1, 8, 8);
    private static final ResourceLocation GLOW = sprite("glow");
    private static final ResourceLocation GLOW_BANDS = sprite("glow_bands");
    private static final ResourceLocation STRIP = sprite("strip");
    private static final ResourceLocation STRIP_DASHED = sprite("strip_dashed");
    private static final ResourceLocation WAVE_EDGE = sprite("wave_edge");
    private static final ResourceLocation RING_BAND = sprite("ring_band");
    private static final ResourceLocation[] SMOKE_PUFFS = particles("big_smoke_", 12);
    private static final ResourceLocation[] EXPLOSION = particles("explosion_", 16);
    private static final ResourceLocation[] SPARK = particles("spark_", 8);
    private static final ResourceLocation[] GLITTER = particles("glitter_", 8);

    static {
        for (int k = 0; k <= WAVE_SEGMENTS; k++) {
            double angle = Math.PI * 2 * k / WAVE_SEGMENTS;
            WAVE_COS[k] = (float) Math.cos(angle);
            WAVE_SIN[k] = (float) Math.sin(angle);
        }
    }

    private final OwnPass pass;
    private final Matrix4f view, projection;
    private final Vec3 camera;
    private final Vector3f right, up;
    private final List<float[]> washes = new ArrayList<>();

    BurnsSkyEffects(OwnPass pass, Matrix4f view, Matrix4f projection, Vec3 camera) {
        this.pass = pass;
        this.view = view;
        this.projection = projection;
        this.camera = camera;
        this.right = new Vector3f(view.m00(), view.m10(), view.m20()).normalize();
        this.up = new Vector3f(view.m01(), view.m11(), view.m21()).normalize();
    }

    private static Atlas atlas(String name, int cols, int rows, int frames) {
        return new Atlas(sprite(name), cols, rows, frames);
    }

    private static ResourceLocation sprite(String name) {
        return Wormholes.id("textures/effect/burns_sky/" + name + ".png");
    }

    private static ResourceLocation[] particles(String prefix, int count) {
        ResourceLocation[] out = new ResourceLocation[count];
        for (int i = 0; i < count; i++) out[i] = ResourceLocation.withDefaultNamespace("textures/particle/" + prefix + i + ".png");
        return out;
    }

    static double reach(Minecraft mc, BurnsSkyClient.Run run, double age, float partialTick, Vec3 centre) {
        double furthest = 0;
        double radius = ballRadius(age);
        double ball = radius > 0 || age >= HELICES && age < VORTEX_END ? run.ball(mc, age, partialTick).distanceTo(centre) : 0;
        if (radius > 0) furthest = ball + radius * BALL_REACH;
        if (age >= HELICES && age < VORTEX_END) furthest = Math.max(furthest, ball + 530 * 5 * UNIT);
        Vec3 feet = run.targetFeet();
        if (age >= CRACKS && age < CRACKS_END) furthest = Math.max(furthest, feet.distanceTo(centre) + 3300 * UNIT);
        if (age >= WAVE) {
            double scale = waveScale(age) / 1.8;
            double height = WAVE_HEIGHT * scale;
            furthest = Math.max(furthest, feet.distanceTo(centre) + WAVE_RADIUS * scale + height * WAVE_LEAN
                    + Math.max(1, height * WAVE_WIDTH) * 0.35 + 3);
        }
        return furthest;
    }

    void draw(Minecraft mc, BurnsSkyClient.Run run, double age, float partialTick) {
        long seed = run.payload.userId() * 0x9E3779B97F4A7C15L;
        washes.clear();
        Vec3 ball = run.ball(mc, age, partialTick), feet = run.targetFeet();
        boolean drawBall = ballScale(age) > 0 && (age >= THROW || outOfMouth(age) < 1
                || !run.touchesBody(ball, ballRadius(age) * BALL_OUTER, run.userAt(mc, partialTick)));
        if (drawBall) ballCore(ball, age);
        if (age >= WAVE && age < EFFECTS_END) shockWave(feet, age, seed);
        if (age >= CRACKS && age < CRACKS_END) ground(feet, age);
        if (age >= SMOKE && age < EFFECTS_END) smoke(feet, age, seed);
        if (age >= PILLAR && age < EFFECTS_END) pillar(feet, age);
        if (age >= DOME && age < DOME_END) dome(feet, age);
        if (age >= PRE_GLOW && age < EFFECTS_END) explosion(feet, age, seed);
        if (age >= LIGHT_BORN && age < BALL_SMALL) birthLight(run.mouth(mc, partialTick), age, seed);
        if (age >= BALL && age < VORTEX_END) charge(ball, age, seed);
        if (age >= HELICES && age < VORTEX_END) helices(ball, age);
        if (age >= GROW_5 && age < THROW) ballBursts(ball, age, seed);
        if (age >= THROW && age < HIT + 2) trail(mc, run, age, partialTick, seed);
        if (drawBall) {
            ballShells(ball, age);
            halo(ball, age);
        }
        screen(mc, run, age, partialTick, seed, ball);
    }

    private void birthLight(Vec3 at, double age, long seed) {
        double t = (age - LIGHT_BORN) / (BALL_SMALL - LIGHT_BORN);
        float flash = (float) Math.max(0, 1 - (age - LIGHT_BORN) / 8);
        VertexConsumer glow = begin(GLOW);
        glow(glow, at, (float) (0.6 + 2.5 * flash), light(BIRTH, flash));
        float steady = (float) Math.min(1, (age - LIGHT_BORN) / 4) * (float) Math.min(1, (1 - t) * 3);
        glow(glow, at, 0.8f, light(PALE, steady));
        if (age < STRIPES) return;
        VertexConsumer streaks = begin(STRIP);
        for (int i = 0; i < 14; i++) {
            long h = mix(seed ^ (i * 104729L));
            double born = STRIPES + unit(h) * (BALL_SMALL - STRIPES - 6);
            double life = (age - born) / 6;
            if (life < 0 || life >= 1) continue;
            Vector3f dir = randomDirection(h);
            Vec3 tip = at.add(dir.x * 2.5, Math.abs(dir.y) * 2.5, dir.z * 2.5);
            ribbon(streaks, at, tip, (float) (0.12 * (1 - life)), 0, 1, light(PALE, (float) (1 - life)));
        }
    }

    private void charge(Vec3 centre, double age, long seed) {
        double radius = ballRadius(age);
        double vortexScale = age < BALL_SMALL ? lerp(age, BALL, BALL_SMALL, 0.05, 0.1) : lerp(age, GROW_3_FROM, GROW_3_TO, 0.1, 2);
        float fade = (float) (Math.min(1, (age - BALL) / 4) * Math.min(1, (VORTEX_END - age) / 4));
        float half = (float) (410 * vortexScale * UNIT);
        VertexConsumer vortex = begin(VORTEX.texture());
        float[] uv = VORTEX.uv(Math.floorMod((int) ((age - BALL) / 3), VORTEX.frames()));
        billboard(vortex, centre, half, half, 0f, uv[0], uv[1], uv[2], uv[3], light(WHITE, fade));

        VertexConsumer glow = begin(GLOW);
        float lime = (float) (0.18 * fade * (0.8 + 0.2 * Math.sin(age * 0.2)));
        glow(glow, centre, (float) (radius * 4 + 1), light(LIME, lime));

        VertexConsumer motes = begin(STRIP);
        double grow = age < at(205) ? lerp(age, BALL, at(205), 0.2, 2) : lerp(age, at(275), at(322), 2, 5);
        for (int i = 0; i < (VORTEX_END - BALL) * 3; i++) {
            long h = mix(seed + i * 31L);
            double born = BALL + i / 3.0;
            double life = 12 + unit(h) * 10;
            double t = (age - born) / life;
            if (t < 0 || t >= 1) continue;
            Vector3f dir = randomDirection(h = mix(h));
            double from = radius + (1 + unit(h = mix(h)) * 2) * grow;
            double distance = Mth.lerp(t * t, from, radius * 0.7);
            double length = (0.3 + 0.8 * t) * grow * 0.5;
            int[] c = MOTE_COLOURS[(int) (unit(h = mix(h)) * MOTE_COLOURS.length)];
            ribbon(motes, along(centre, dir, distance), along(centre, dir, distance + length), (float) (0.04 * grow + 0.03), 0, 1,
                    light(c, (float) (fade * Math.min(1, t * 4) * 0.9)));
        }
    }

    private void helices(Vec3 ball, double age) {
        double sx = lerp(age, HELICES, GROW_3_TO, 0.5, 5);
        double sy = lerp(age, HELICES, GROW_3_TO, 0.5, 10);
        float fade = (float) (smooth((age - HELICES) / (HELICES_FADE_IN - HELICES)) * Math.min(1, (VORTEX_END - age) / 4));
        if (fade <= 0) return;
        double outer = 530 * sx * UNIT, inner = ballRadius(age) * 0.8;
        double bottom = (-390 * sy + 50) * UNIT, top = (-141 * sy + 50) * UNIT;
        int segments = 64;
        for (int layer = 0; layer < 2; layer++) {
            VertexConsumer out = begin(layer == 0 ? STRIP : STRIP_DASHED);
            float[] colour = layer == 0 ? light(GOLD, fade * 0.9f) : light(DASHED_GOLD, fade * 0.7f);
            float widen = layer == 0 ? 0.6f : 1f, stretch = layer == 0 ? 1f : 0.5f;
            for (int helix = 0; helix < 2; helix++) {
                double turn = helix == 0 ? 1 : -1;
                Vec3 prev = null;
                for (int i = 0; i <= segments; i++) {
                    double u = (double) i / segments;
                    double angle = turn * (u * Math.PI * 3.5 + age * 0.09) + helix * Math.PI;
                    double radius = Mth.lerp(Math.pow(u, 0.7), outer, inner);
                    double y = u < 0.8 ? Mth.lerp(u / 0.8, bottom, top) : Mth.lerp((u - 0.8) / 0.2, top, 0);
                    Vec3 p = ball.add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                    if (prev != null) {
                        double u0 = (double) (i - 1) / segments;
                        float width = (float) (Mth.lerp(u, 1.2, 0.3) * sx / 5 + 0.1);
                        float v0 = (float) (u0 * 6 - age * 0.06), v1 = (float) (u * 6 - age * 0.06);
                        ribbon(out, prev, p, width * widen, v0 * stretch, v1 * stretch, colour);
                    }
                    prev = p;
                }
            }
        }
    }

    private void ballBursts(Vec3 ball, double age, long seed) {
        double radius = ballRadius(age);
        for (int ringAt : RINGS) {
            double t = (age - ringAt) / (49 * 2.0 / 3 * PACE);
            if (t < 0 || t >= 1) continue;
            float half = (float) (165 * UNIT * ballScale(age) * RING_SCALE);
            VertexConsumer ring = begin(SMOKE_RING.texture());
            float fade = (float) Math.min(1, (1 - t) * 3);
            flat(ring, SMOKE_RING, (int) (t * SMOKE_RING.frames()), ball.subtract(0, radius * 0.2, 0), half, light(WHITE, fade * 0.8f));
        }
        for (int r = 0; r < RAYS.length; r++) {
            double t = (age - RAYS[r]) / at(30);
            if (t < 0 || t >= 1) continue;
            double scale = r == 0 ? lerp(age, RAYS[0], at(462), 3, 10) : lerp(age, RAYS[1], at(494), 10, 15);
            VertexConsumer rays = begin(STRIP);
            for (int i = 0; i < 28; i++) {
                long h = mix(seed + r * 7777L + i * 131L);
                Vector3f dir = randomDirection(h);
                double length = radius * 0.4 + scale * 0.35 * (0.5 + unit(h = mix(h)));
                Vec3 a = ball.add(dir.x * radius * 0.6, dir.y * radius * 0.6, dir.z * radius * 0.6);
                int[] c = RAY_COLOURS[(int) (unit(h = mix(h)) * RAY_COLOURS.length)];
                ribbon(rays, a, along(ball, dir, radius + length), (float) (0.08 * scale), 0, 1,
                        light(c, (float) ((1 - t) * Math.min(1, t * 6))));
            }
        }
        List<Sprite> grains = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            long h = mix(seed + 99991L + i);
            double t = ((age - GROW_5) / 18 + unit(h)) % 1;
            Vector3f dir = randomDirection(h = mix(h));
            int frame = (int) (Math.sin(t * Math.PI) * (GLITTER.length - 0.01));
            grains.add(new Sprite(along(ball, dir, radius * (1.6 - 0.6 * t)), (float) (radius * 0.06 + 0.15), frame, light(PALE, 1f)));
        }
        sprites(GLITTER, grains);
    }

    private void trail(Minecraft mc, BurnsSkyClient.Run run, double age, float partialTick, long seed) {
        VertexConsumer glow = begin(GLOW);
        for (int spawn = (int) Math.max(THROW, age - 14); spawn <= (int) age && spawn < HIT; spawn++) {
            double life = (age - spawn) / 14;
            if (life < 0 || life >= 1) continue;
            Vec3 at = run.ball(mc, spawn, partialTick);
            double radius = ballRadius(spawn);
            for (int k = 0; k < 3; k++) {
                Vector3f dir = randomDirection(mix(seed + spawn * 3L + k));
                Vec3 p = along(at, dir, radius * 0.5 + radius * 0.4 * life);
                int[] c = spawn < THROWN ? GOLD : TRAIL_COLOURS[k];
                float size = (float) (radius * 0.45 * (1 - life) + 0.2);
                glow(glow, p, size, light(c, (float) (0.8 * (1 - life)) * near(p, size)));
            }
        }
    }

    private static float ballAlpha(double age) {
        double in = Mth.clamp((age - BALL) / 2, 0, 1);
        double out = Mth.clamp((BALL_GONE - age) / 6, 0, 1);
        return (float) Math.min(in, out);
    }

    private float near(Vec3 at, double radius) {
        double gap = rel(at).length() - radius;
        return (float) Mth.clamp((gap - 2) / 4, 0, 1);
    }

    private void ballCore(Vec3 centre, double age) {
        float alpha = ballAlpha(age);
        if (alpha <= 0) return;
        double half = ballRadius(age) * SHELL_SIZES[0];
        double spin = age * SHELL_SPINS[0];
        if (inside(centre, half, spin)) {
            washes.add(paint(WHITE, alpha));
            return;
        }
        cube(begin(CUBE.texture(), WarpRenderTypes.BURNS_SKY_SOLID), centre, half, spin, CUBE.uv(0), paint(WHITE, alpha));
    }

    private void ballShells(Vec3 centre, double age) {
        float alpha = ballAlpha(age);
        if (alpha <= 0) return;
        double radius = ballRadius(age);
        int frame = Math.floorMod((int) (age / 3), 4);
        VertexConsumer out = begin(CUBE.texture());
        int glow = SHELL_SIZES.length - 1;
        for (int s = 1; s < SHELL_SIZES.length; s++) {
            double half = radius * SHELL_SIZES[s];
            double spin = age * SHELL_SPINS[s];
            if (inside(centre, half, spin)) {
                washes.add(light(SHELL_COLOURS[s], SHELL_LIGHT[s] * alpha * 0.5f));
                continue;
            }
            int tile = s == glow ? 1 : 4 * s + frame;
            cube(out, centre, half, spin, CUBE.uv(tile), light(s == glow ? SHELL_COLOURS[s] : WHITE, SHELL_LIGHT[s] * alpha));
        }
        float flash = 0;
        for (int step : GROWTH_STEPS) {
            double t = (age - step) / 8;
            if (t >= 0 && t < 1) flash = Math.max(flash, (float) (1 - t));
        }
        double half = radius * BALL_OUTER;
        if (flash > 0 && !inside(centre, half, 0)) cube(out, centre, half, 0, CUBE.uv(1), light(WHITE, flash * alpha));
    }

    private void halo(Vec3 centre, double age) {
        float alpha = ballAlpha(age);
        double half = ballRadius(age) * BALL_OUTER;
        if (alpha <= 0 || rel(centre).length() < half * HALO[2]) return;
        VertexConsumer glow = begin(GLOW);
        glow(glow, centre, (float) (half * HALO[2]), light(LIME, (float) HALO[3] * alpha));
        glow(glow, centre, (float) (half * HALO[0]), light(SHELL_COLOURS[1], (float) HALO[1] * alpha));
    }

    private boolean inside(Vec3 centre, double half, double spin) {
        Vector3f d = rel(centre).negate();
        float cos = (float) Math.cos(spin), sin = (float) Math.sin(spin);
        double x = d.x * cos + d.z * sin, z = -d.x * sin + d.z * cos;
        double reach = half + INSIDE_MARGIN;
        return Math.abs(x) < reach && Math.abs(d.y) < reach && Math.abs(z) < reach;
    }

    private void cube(VertexConsumer out, Vec3 centre, double half, double spin, float[] uv, float[] c) {
        Vector3f o = rel(centre);
        float h = (float) half, cos = (float) Math.cos(spin), sin = (float) Math.sin(spin);
        Vector3f x = new Vector3f(cos * h, 0, sin * h), y = new Vector3f(0, h, 0), z = new Vector3f(-sin * h, 0, cos * h);
        Vector3f[][] faces = {{x, z, y}, {new Vector3f(x).negate(), z, y}, {y, x, z}, {new Vector3f(y).negate(), x, z},
                {z, x, y}, {new Vector3f(z).negate(), x, y}};
        for (Vector3f[] face : faces) quad(out, new Vector3f(o).add(face[0]), face[1], face[2], uv[0], uv[1], uv[2], uv[3], c);
    }

    private void ground(Vec3 feet, double age) {
        double local = (age - CRACKS) * 1.5;
        float out = (float) Mth.clamp((CRACKS_END - age) / 6, 0, 1);
        double crack = (local - 4) / 6;
        if (crack >= 0) {
            float k = (float) Math.min(1, local / 4) * out;
            VertexConsumer cracks = begin(CRACK_FRAMES.texture());
            flat(cracks, CRACK_FRAMES, Math.min((int) crack, CRACK_FRAMES.frames() - 1), feet.add(0, 0.04, 0), (float) (500 * UNIT),
                    light(WHITE, k));
        }
        double band = (local - 6) / (FIRE_WALL_TICKS * 1.5);
        if (band >= 0 && band < 1) {
            float k = (float) Math.min(1, (1 - band) * 4);
            double rise = Math.min(1, band * 6) * Math.min(1, (1 - band) * 2.5);
            int frame = (int) (age / 2);
            flameRing(feet, 2258 * UNIT, rise, frame, k, 0f);
            flameRing(feet, 2940 * UNIT, rise * 0.8, frame + 2, k * 0.8f, 0.37f);
        }
    }

    private void flameRing(Vec3 feet, double radius, double rise, int frame, float k, float turn) {
        double height = FLAME_HEIGHT * Math.max(0.2, rise);
        int repeats = Math.max(1, (int) Math.round(radius * Math.PI * 2 / FLAME_TILE));
        float[] uv = FLAME_WALL.uv(Math.floorMod(frame, FLAME_WALL.frames()));
        wall(begin(FLAME_WALL.texture()), feet, radius, height * FLAME_LEAN, height, uv[1], uv[3], turn, repeats, WHITE, k, 0);
        floorRing(begin(RING_BAND), feet, radius - FLAME_GLOW_WIDTH, radius + FLAME_GLOW_WIDTH, turn, repeats,
                light(FLAME_GLOW, k * 0.45f));
    }

    private void dome(Vec3 feet, double age) {
        double scale = age < DOME_FULL ? lerp(age, DOME, DOME_FULL, 0.5, 8) : lerp(age, DOME_SHRINK_FROM, DOME_SHRINK_TO, 8, 2);
        float fade = (float) Mth.clamp((DOME_END - age) / 8, 0, 1);
        double radius = 115 * scale * UNIT, height = 57.5 * scale * UNIT + 30 * scale * UNIT;
        float[] uv = DOME_LIGHT.uv(Math.floorMod((int) ((age - DOME) / 2), DOME_LIGHT.frames()));
        float x = (float) (feet.x - camera.x), y = (float) (feet.y - camera.y), z = (float) (feet.z - camera.z);
        int repeats = Math.max(1, (int) Math.round(2 * Math.PI * radius / (PIXEL * DOME_TEX_WIDTH)));
        stepped(begin(DOME_LIGHT.texture()), x, y, z, radius, height, t -> Math.sqrt(1 - t * t), 0, repeats, uv, DOME_TEX_HEIGHT,
                DOME_TINT, fade, DOME_CORE);
        float spread = (float) (radius * 2.3);
        quad(begin(GLOW_BANDS), rel(feet.add(0, 0.05, 0)), new Vector3f(spread, 0, 0), new Vector3f(0, 0, -spread), 0, 0, 1, 1,
                light(WHITE, fade * 0.9f));
    }

    private void pillar(Vec3 feet, double age) {
        boolean rising = age < PILLAR_RISEN;
        double sx = rising ? lerp(age, PILLAR, PILLAR_RISEN, 0.05, 1) : lerp(age, PILLAR_GROW_FROM, EFFECTS_END, 1, 6);
        double sy = rising ? lerp(age, PILLAR, PILLAR_RISEN, 0.05, 0.5) : lerp(age, PILLAR_GROW_FROM, EFFECTS_END, 0.5, 3);
        float fade = (float) Mth.clamp((EFFECTS_END - age) / 6, 0, 1) * (float) Math.min(1, (age - PILLAR) / 3);
        double life = (age - PILLAR) / (EFFECTS_END - PILLAR);
        double radius = Math.max(0.75, 330 * sx * UNIT * PILLAR_WIDTH), height = Math.max(1, (1095 * sy + 75) * UNIT);
        float[] uv = PILLAR_FLAME.uv(Math.floorMod((int) (age / 2), 4) + (life < 0.5 ? 0 : 4));
        Vec3 toCamera = new Vec3(camera.x - feet.x, 0, camera.z - feet.z);
        Vec3 across = toCamera.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : new Vec3(toCamera.z, 0, -toCamera.x).normalize();
        Vector3f wide = new Vector3f((float) (across.x * radius), 0, (float) (across.z * radius));
        quad(begin(PILLAR_FLAME.texture()), rel(feet.add(0, height / 2, 0)), wide, new Vector3f(0, (float) (height / 2), 0),
                uv[0], uv[1], uv[2], uv[3], light(WHITE, fade));
        int redder = (int) Math.round(life * 2);
        float spread = (float) (radius * 1.6);
        quad(begin(GLOW_BANDS), rel(feet.add(0, 0.05, 0)), new Vector3f(spread, 0, 0), new Vector3f(0, 0, -spread), 0, 0, 1, 1,
                light(PILLAR_COLOURS[Math.min(PILLAR_COLOURS.length - 1, redder + 1)], fade * 0.45f));
    }

    /**
     * A body of revolution round (x, y, z) in PIXEL terraces: radius times across(t) at each band's middle, rounded to
     * whole PIXELs, each band an upright ring with a flat ledge where the next steps in. Faced with a flipbook frame
     * repeated `repeats` times round, brighter where seen edge on (core where seen face on).
     */
    private static void stepped(VertexConsumer out, float x, float y, float z, double radius, double height, DoubleUnaryOperator across,
                                float turn, int repeats, float[] uv, int texRows, int[] c, float fade, float core) {
        int steps = Math.max(1, (int) (height / PIXEL));
        double[] r = new double[steps + 1];
        for (int k = 0; k < steps; k++) r[k] = Math.round(radius * across.applyAsDouble((k + 0.5) / steps) / PIXEL) * PIXEL;
        for (int k = 0; k < steps; k++) {
            if (r[k] <= 0) continue;
            float low = (float) (k * PIXEL), high = (float) ((k + 1) * PIXEL), wide = (float) r[k], next = (float) r[k + 1];
            for (int i = 0; i < WAVE_SEGMENTS; i++) {
                stepVertex(out, x, y, z, i, wide, low, false, turn, repeats, uv, texRows, c, fade, core);
                stepVertex(out, x, y, z, i + 1, wide, low, false, turn, repeats, uv, texRows, c, fade, core);
                stepVertex(out, x, y, z, i + 1, wide, high, false, turn, repeats, uv, texRows, c, fade, core);
                stepVertex(out, x, y, z, i, wide, high, false, turn, repeats, uv, texRows, c, fade, core);
                if (next >= wide) continue;
                stepVertex(out, x, y, z, i, next, high, true, turn, repeats, uv, texRows, c, fade, core);
                stepVertex(out, x, y, z, i + 1, next, high, true, turn, repeats, uv, texRows, c, fade, core);
                stepVertex(out, x, y, z, i + 1, wide, high, true, turn, repeats, uv, texRows, c, fade, core);
                stepVertex(out, x, y, z, i, wide, high, true, turn, repeats, uv, texRows, c, fade, core);
            }
        }
    }

    private static void stepVertex(VertexConsumer out, float x, float y, float z, int i, float radius, float up, boolean ledge,
                                   float turn, int repeats, float[] uv, int texRows, int[] c, float fade, float core) {
        float px = x + WAVE_COS[i] * radius, py = y + up, pz = z + WAVE_SIN[i] * radius;
        Vector3f normal = ledge ? new Vector3f(0, 1, 0) : new Vector3f(WAVE_COS[i], 0, WAVE_SIN[i]);
        Vector3f towards = new Vector3f(px, py, pz);
        float facing = towards.lengthSquared() > 0 ? Math.abs(normal.dot(towards.normalize())) : 1;
        float k = fade * (core + (1 - core) * (1 - facing));
        float u = (float) i * repeats / WAVE_SEGMENTS + turn;
        float v = Mth.lerp(Math.min(1 - 0.5f / texRows, up / (float) (PIXEL * texRows)), uv[3], uv[1]);
        out.addVertex(px, py, pz).setUv(u, v).setColor(c[0] / 255f * k, c[1] / 255f * k, c[2] / 255f * k, 0f);
    }

    private void smoke(Vec3 feet, double age, long seed) {
        float end = (float) Mth.clamp((EFFECTS_END - age) / 10, 0, 1);
        List<Sprite> puffs = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            long h = mix(seed + 555L + i * 17L);
            double born = SMOKE + i * 1.6;
            double t = (age - born) / 34;
            if (t < 0 || t >= 1) continue;
            double angle = unit(h) * Math.PI * 2;
            double ring = 1.5 + 3 * unit(h = mix(h));
            Vec3 at = feet.add(Math.cos(angle) * ring, 0.8 + t * 2.5 + unit(h = mix(h)), Math.sin(angle) * ring);
            float a = (float) (0.4 * Math.sin(Math.PI * t)) * end;
            float size = (float) (0.75 + 0.75 * Math.floor(t * 3));
            puffs.add(new Sprite(at, size, Math.min(SMOKE_PUFFS.length - 1, (int) (t * SMOKE_PUFFS.length)), paint(step(SMOKE_COLOURS, t), a)));
        }
        sprites(SMOKE_PUFFS, puffs);
    }

    private void explosion(Vec3 feet, double age, long seed) {
        if (age < PRE_GLOW_END) {
            Vec3 core = feet.add(0, 1, 0);
            double t = (age - PRE_GLOW) / (PRE_GLOW_END - PRE_GLOW);
            float k = (float) (Math.min(1, (age - PRE_GLOW) / 3) * (1 - t));
            VertexConsumer glow = begin(GLOW_BANDS);
            glow(glow, core, 3f, light(WHITE, k));
            glow(glow, core, 5f, light(PRE_GLOW_GOLD, k * 0.7f));
            glow(glow, core, 7f, light(PRE_GLOW_ORANGE, k * 0.4f));
            glow(glow, core, 2f, light(PRE_GLOW_BLUE, k * 0.5f));
            VertexConsumer shine = begin(STRIP);
            float spin = (float) (age * 0.05);
            billboard(shine, core, 0.5f, (float) (9 * k + 1), spin, 0, 0, 1, 1, light(PALE, k));
            billboard(shine, core, 0.5f, (float) (9 * k + 1), spin + (float) (Math.PI / 2), 0, 0, 1, 1, light(PALE, k));
        }
        if (age >= FIREBALL) {
            double life = (age - FIREBALL) / (EFFECTS_END - FIREBALL);
            float end = (float) Mth.clamp((EFFECTS_END - age) / 10, 0, 1);
            int[] c = step(FIRE_COLOURS, life);
            List<Sprite> puffs = new ArrayList<>();
            for (int i = 0; i < 14; i++) {
                long h = mix(seed + 4242L + i);
                double cycle = (age - FIREBALL) / PUFF_TICKS + unit(h);
                long round = (long) Math.floor(cycle);
                long p = mix(h + round * 7919L);
                double angle = unit(p) * Math.PI * 2;
                double ring = (0.5 + 1.5 * unit(p = mix(p))) * (1 + life);
                Vec3 at = feet.add(Math.cos(angle) * ring, 0.6 + 2 * unit(p = mix(p)) * (1 + life), Math.sin(angle) * ring);
                float size = (float) (0.5 * Math.ceil((1 + unit(mix(p))) * (1 + life)));
                puffs.add(new Sprite(at, size, (int) ((cycle - round) * EXPLOSION.length), light(c, 0.3f * end)));
            }
            sprites(EXPLOSION, puffs);
            VertexConsumer rings = begin(RING_BAND);
            for (int i = 0; i < 8; i++) {
                double t = (age - FIREBALL - i * 13) / 30;
                if (t < 0 || t >= 1) continue;
                double radius = 1 + t * 9;
                float k = (float) ((1 - t) * 0.8) * end;
                floorRing(rings, feet, radius - RING_WIDTH, radius + RING_WIDTH, i * 0.31f, RING_REPEATS,
                        light(RAINBOW[i % RAINBOW.length], k));
            }
        }
        if (age >= SPARKS) {
            float end = (float) Mth.clamp((EFFECTS_END - age) / 6, 0, 1);
            List<Sprite> sparks = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                long h = mix(seed + 8080L + i);
                double born = SPARKS + unit(h) * (EFFECTS_END - SPARKS - 10);
                double t = (age - born) / 16;
                if (t < 0 || t >= 1) continue;
                double angle = unit(h = mix(h)) * Math.PI * 2;
                double ring = 4 * unit(h = mix(h));
                Vec3 at = feet.add(Math.cos(angle) * ring, 2 + t * 10, Math.sin(angle) * ring);
                int frame = Math.min(SPARK.length - 1, (int) ((1 - t) * SPARK.length));
                sparks.add(new Sprite(at, 0.35f, frame, light(WHITE, end)));
            }
            sprites(SPARK, sparks);
        }
    }

    private void shockWave(Vec3 feet, double age, long seed) {
        double scale = waveScale(age);
        double radius = WAVE_RADIUS * scale / 1.8, height = WAVE_HEIGHT * scale / 1.8;
        double width = Math.max(1, height * WAVE_WIDTH), lean = height * WAVE_LEAN;
        float end = (float) Mth.clamp((EFFECTS_END - age) / 14, 0, 1) * (float) Math.min(1, (age - WAVE) / 3);
        float turn = (float) unit(seed);
        int frame = Math.floorMod((int) ((age - WAVE) / 3), WAVE_CLOUD.frames());
        VertexConsumer clouds = begin(WAVE_CLOUD.texture());
        float[] back = WAVE_CLOUD.uv((frame + 2) % WAVE_CLOUD.frames()), front = WAVE_CLOUD.uv(frame);
        wall(clouds, feet, radius - width * 0.7, lean * 0.5, height * 0.8, back[1], back[3], turn + 0.5f, WAVE_REPEATS,
                CLOUD_BACK, end, WAVE_BODY);
        wall(clouds, feet, radius, lean, height, front[1], front[3], turn, WAVE_REPEATS, CLOUD_FRONT, end, WAVE_BODY);
        VertexConsumer edge = begin(WAVE_EDGE);
        wall(edge, feet, radius + width * 0.35, 0, height * 0.3, 0, 1, turn, EDGE_REPEATS, CLOUD_TOP, end, 0);
        floorRing(edge, feet, radius - width, radius + width * 0.35, turn, EDGE_REPEATS, light(CLOUD, end * 0.6f));
        List<Sprite> dust = new ArrayList<>(WAVE_PUFFS);
        for (int i = 0; i < WAVE_PUFFS; i++) {
            long h = mix(seed + 7483L + i * 13L);
            double t = ((age - WAVE) / 14 + unit(h)) % 1;
            double angle = (i + unit(h = mix(h))) * Math.PI * 2 / WAVE_PUFFS;
            double out = radius - width * 0.4 + width * 0.5 * t;
            Vec3 at = feet.add(Math.cos(angle) * out, height * (0.05 + 0.12 * t), Math.sin(angle) * out);
            float half = (float) Math.max(0.5, 0.5 * Math.ceil(height * (0.12 + 0.08 * unit(mix(h))) * (1.2 + 0.8 * t)));
            float a = (float) (0.5 * Math.sin(Math.PI * t)) * end * near(at, half);
            dust.add(new Sprite(at, half, Math.min(SMOKE_PUFFS.length - 1, (int) (t * SMOKE_PUFFS.length)), haze(step(WAVE_DUST, t), a)));
        }
        sprites(SMOKE_PUFFS, dust);
    }

    private void wall(VertexConsumer out, Vec3 feet, double foot, double lean, double height, float v0, float v1, float turn,
                      int repeats, int[] colour, float k, float body) {
        float x = (float) (feet.x - camera.x), y = (float) (feet.y - camera.y), z = (float) (feet.z - camera.z);
        float top = y + (float) height, r0 = (float) Math.max(0, foot), r1 = r0 + (float) lean;
        float rise = y > 0 ? y : top < 0 ? -top : 0, du = (float) repeats / WAVE_SEGMENTS;
        float r = colour[0] / 255f * k, g = colour[1] / 255f * k, b = colour[2] / 255f * k, a = body * k;
        float prev = closeness(x + WAVE_COS[0] * r0, z + WAVE_SIN[0] * r0, rise);
        for (int i = 0; i < WAVE_SEGMENTS; i++) {
            float next = closeness(x + WAVE_COS[i + 1] * r0, z + WAVE_SIN[i + 1] * r0, rise);
            float u0 = turn + i * du, u1 = u0 + du;
            out.addVertex(x + WAVE_COS[i] * r0, y, z + WAVE_SIN[i] * r0).setUv(u0, v1).setColor(r * prev, g * prev, b * prev, a * prev);
            out.addVertex(x + WAVE_COS[i + 1] * r0, y, z + WAVE_SIN[i + 1] * r0).setUv(u1, v1).setColor(r * next, g * next, b * next, a * next);
            out.addVertex(x + WAVE_COS[i + 1] * r1, top, z + WAVE_SIN[i + 1] * r1).setUv(u1, v0).setColor(r * next, g * next, b * next, a * next);
            out.addVertex(x + WAVE_COS[i] * r1, top, z + WAVE_SIN[i] * r1).setUv(u0, v0).setColor(r * prev, g * prev, b * prev, a * prev);
            prev = next;
        }
    }

    private static float closeness(float x, float z, float rise) {
        return Mth.clamp(((float) Math.sqrt(x * x + z * z + rise * rise) - 1) / 6, WAVE_NEAR, 1);
    }

    private void floorRing(VertexConsumer out, Vec3 feet, double inner, double outer, float turn, int repeats, float[] c) {
        float x = (float) (feet.x - camera.x), y = (float) (feet.y + 0.05 - camera.y), z = (float) (feet.z - camera.z);
        float ri = (float) Math.max(0, inner), ro = (float) outer, du = (float) repeats / WAVE_SEGMENTS;
        for (int i = 0; i < WAVE_SEGMENTS; i++) {
            float u0 = turn + i * du, u1 = u0 + du;
            out.addVertex(x + WAVE_COS[i] * ri, y, z + WAVE_SIN[i] * ri).setUv(u0, 0).setColor(c[0], c[1], c[2], c[3]);
            out.addVertex(x + WAVE_COS[i + 1] * ri, y, z + WAVE_SIN[i + 1] * ri).setUv(u1, 0).setColor(c[0], c[1], c[2], c[3]);
            out.addVertex(x + WAVE_COS[i + 1] * ro, y, z + WAVE_SIN[i + 1] * ro).setUv(u1, 1).setColor(c[0], c[1], c[2], c[3]);
            out.addVertex(x + WAVE_COS[i] * ro, y, z + WAVE_SIN[i] * ro).setUv(u0, 1).setColor(c[0], c[1], c[2], c[3]);
        }
    }

    private void screen(Minecraft mc, BurnsSkyClient.Run run, double age, float partialTick, long seed, Vec3 ball) {
        float warm = 0;
        if (age >= at(285) && age < THROW) warm = (float) (0.07 * Math.min(1, (age - at(285)) / 10));
        if (age >= WARM && age < EFFECTS_END) warm = (float) (0.09 * Math.min(1, (age - WARM) / 10) * Mth.clamp((EFFECTS_END - age) / 6, 0, 1));
        if (warm > 0) wash(WarpRenderTypes.BURNS_SKY_SCREEN_TAKE_AWAY, light(TAKE_AWAY, warm));
        for (float[] wash : washes) wash(WarpRenderTypes.BURNS_SKY_SCREEN_LIGHT, wash);
        float zoom = 0;
        for (int[] range : ZOOM_LINES) {
            if (age >= range[0] && age < range[1]) zoom = (float) Math.min(1, Math.min((age - range[0]) / 3, (range[1] - age) / 3));
        }
        if (zoom > 0) {
            Vec3 focus = age < CHARGE ? run.mouth(mc, partialTick) : age < HIT ? ball : run.targetFeet();
            zoomLines(focus, zoom, seed + (long) age * 7);
        }
        if (age >= LINES_UP_FROM && age < LINES_UP_TO) {
            float k = (float) Math.min(1, Math.min((age - LINES_UP_FROM) / 3, (LINES_UP_TO - age) / 3));
            risingLines(age, k, seed);
        }
    }

    private void wash(WarpRenderTypes.BurnsSky blend, float[] c) {
        VertexConsumer out = pass.begin(blend.of(GLOW, false), new Matrix4f());
        for (float[] k : SCREEN_CORNERS) {
            out.addVertex((k[0] * 1.1f + projection.m20()) / projection.m00(), (k[1] * 1.1f + projection.m21()) / projection.m11(), -1f)
                    .setUv(0.5f, 0.5f).setColor(c[0], c[1], c[2], c[3]);
        }
    }

    private VertexConsumer screenLines() {
        return pass.begin(WarpRenderTypes.SCREEN, new Matrix4f());
    }

    private void zoomLines(Vec3 focus, float strength, long seed) {
        Vector4f point = screenPoint(focus);
        float fx = point != null ? Mth.clamp(point.x, -0.8f, 0.8f) : 0, fy = point != null ? Mth.clamp(point.y, -0.8f, 0.8f) : 0;
        VertexConsumer out = screenLines();
        float aspect = projection.m11() / projection.m00();
        for (int i = 0; i < 70; i++) {
            long h = mix(seed + i);
            double angle = unit(h) * Math.PI * 2;
            float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle) * aspect;
            float outer = 2.2f, inner = (float) (0.75 + 0.6 * unit(h = mix(h)));
            float width = (float) (0.004 + 0.01 * unit(h = mix(h)));
            float tx = fx + dx * inner, ty = fy + dy * inner;
            float bx = fx + dx * outer, by = fy + dy * outer;
            float px = -dy * width, py = dx * width;
            int alpha = (int) (strength * (90 + 100 * unit(h = mix(h))));
            ndcVertex(out, tx, ty, 255, 255, 255, alpha);
            ndcVertex(out, bx + px, by + py, 255, 255, 255, alpha);
            ndcVertex(out, bx - px, by - py, 255, 255, 255, alpha);
            ndcVertex(out, bx - px, by - py, 255, 255, 255, alpha);
        }
    }

    private void risingLines(double age, float strength, long seed) {
        VertexConsumer out = screenLines();
        for (int i = 0; i < 40; i++) {
            long h = mix(seed + 2024L + i);
            float x = (float) (unit(h) * 2 - 1);
            float speed = (float) (0.15 + 0.15 * unit(h = mix(h)));
            float length = (float) (0.3 + 0.5 * unit(h = mix(h)));
            float y = (float) (((age * speed + unit(h = mix(h)) * 3) % 3) - 1.5 - length);
            float w = (float) (0.002 + 0.004 * unit(h = mix(h)));
            for (int s = 0; s < 3; s++) {
                float y0 = y + length * s / 3, y1 = y + length * (s + 1) / 3;
                int a = (int) (strength * 160 * (s + 1) / 3);
                ndcVertex(out, x - w, y0, 255, 255, 130, a);
                ndcVertex(out, x + w, y0, 255, 255, 130, a);
                ndcVertex(out, x + w, y1, 255, 255, 130, a);
                ndcVertex(out, x - w, y1, 255, 255, 130, a);
            }
        }
    }

    private @Nullable Vector4f screenPoint(Vec3 at) {
        Vector4f clip = new Vector4f((float) (at.x - camera.x), (float) (at.y - camera.y), (float) (at.z - camera.z), 1f);
        view.transform(clip);
        projection.transform(clip);
        if (clip.w < 0.1f) return null;
        return new Vector4f(clip.x / clip.w, clip.y / clip.w, 0, clip.w);
    }

    private void ndcVertex(VertexConsumer out, float x, float y, int r, int g, int b, int a) {
        out.addVertex((x + projection.m20()) / projection.m00(), (y + projection.m21()) / projection.m11(), -1f).setColor(r, g, b, a);
    }

    private VertexConsumer begin(ResourceLocation texture) {
        return begin(texture, WarpRenderTypes.BURNS_SKY_LIGHT);
    }

    private VertexConsumer begin(ResourceLocation texture, WarpRenderTypes.BurnsSky blend) {
        boolean repeat = texture == STRIP || texture == STRIP_DASHED || texture == WAVE_CLOUD.texture() || texture == WAVE_EDGE
                || texture == FLAME_WALL.texture() || texture == DOME_LIGHT.texture() || texture == RING_BAND;
        return pass.begin(blend.of(texture, repeat), view);
    }

    private void sprites(ResourceLocation[] frames, List<Sprite> sprites) {
        for (int f = 0; f < frames.length; f++) {
            VertexConsumer out = null;
            for (Sprite s : sprites) {
                if (Mth.clamp(s.frame(), 0, frames.length - 1) != f) continue;
                if (out == null) out = begin(frames[f]);
                glow(out, s.at(), s.half(), s.colour());
            }
        }
    }

    private Vector3f rel(Vec3 at) {
        return rel(at.x, at.y, at.z);
    }

    private Vector3f rel(double x, double y, double z) {
        return new Vector3f((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z));
    }

    private static Vec3 along(Vec3 from, Vector3f dir, double distance) {
        return from.add(dir.x * distance, dir.y * distance, dir.z * distance);
    }

    private static float[] light(int[] c, float k) {
        return new float[]{c[0] / 255f * k, c[1] / 255f * k, c[2] / 255f * k, 0f};
    }

    private static float[] haze(int[] c, float k) {
        return new float[]{c[0] / 255f * k, c[1] / 255f * k, c[2] / 255f * k, k * WAVE_BODY};
    }

    private static float[] paint(int[] c, float a) {
        return new float[]{c[0] / 255f * a, c[1] / 255f * a, c[2] / 255f * a, a};
    }

    private static int[] step(int[][] keys, double t) {
        return keys[Mth.clamp((int) (t * keys.length), 0, keys.length - 1)];
    }

    private void flat(VertexConsumer out, Atlas atlas, int frame, Vec3 at, float half, float[] c) {
        float[] uv = atlas.uv(frame);
        quad(out, rel(at), new Vector3f(half, 0, 0), new Vector3f(0, 0, -half), uv[0], uv[1], uv[2], uv[3], c);
    }

    private void glow(VertexConsumer out, Vec3 at, float half, float[] c) {
        billboard(out, at, half, half, 0f, 0, 0, 1, 1, c);
    }

    private void billboard(VertexConsumer out, Vec3 at, float halfW, float halfH, float angle, float u0, float v0, float u1, float v1, float[] c) {
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        Vector3f x = new Vector3f(right).mul(cos * halfW).add(new Vector3f(up).mul(sin * halfW));
        Vector3f y = new Vector3f(up).mul(cos * halfH).sub(new Vector3f(right).mul(sin * halfH));
        quad(out, rel(at), x, y, u0, v0, u1, v1, c);
    }

    private static void quad(VertexConsumer out, Vector3f centre, Vector3f x, Vector3f y, float u0, float v0, float u1, float v1, float[] c) {
        corner(out, centre, x, y, -1, -1, u0, v1, c);
        corner(out, centre, x, y, 1, -1, u1, v1, c);
        corner(out, centre, x, y, 1, 1, u1, v0, c);
        corner(out, centre, x, y, -1, 1, u0, v0, c);
    }

    private static void corner(VertexConsumer out, Vector3f centre, Vector3f x, Vector3f y, int sx, int sy, float u, float v, float[] c) {
        out.addVertex(centre.x + x.x * sx + y.x * sy, centre.y + x.y * sx + y.y * sy, centre.z + x.z * sx + y.z * sy)
                .setUv(u, v).setColor(c[0], c[1], c[2], c[3]);
    }

    private void ribbon(VertexConsumer out, Vec3 from, Vec3 to, float width, float v0, float v1, float[] c) {
        Vector3f a = rel(from), b = rel(to);
        Vector3f along = new Vector3f(b).sub(a);
        Vector3f side = new Vector3f(along).cross(new Vector3f(a).add(b).mul(0.5f));
        if (side.lengthSquared() < 1e-10f) return;
        side.normalize().mul(width);
        out.addVertex(a.x - side.x, a.y - side.y, a.z - side.z).setUv(0, v0).setColor(c[0], c[1], c[2], c[3]);
        out.addVertex(a.x + side.x, a.y + side.y, a.z + side.z).setUv(1, v0).setColor(c[0], c[1], c[2], c[3]);
        out.addVertex(b.x + side.x, b.y + side.y, b.z + side.z).setUv(1, v1).setColor(c[0], c[1], c[2], c[3]);
        out.addVertex(b.x - side.x, b.y - side.y, b.z - side.z).setUv(0, v1).setColor(c[0], c[1], c[2], c[3]);
    }

    private static Vector3f randomDirection(long h) {
        double z = unit(h) * 2 - 1;
        double angle = unit(mix(h)) * Math.PI * 2;
        double ring = Math.sqrt(1 - z * z);
        return new Vector3f((float) (ring * Math.cos(angle)), (float) z, (float) (ring * Math.sin(angle)));
    }

    static long mix(long h) {
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }
}
