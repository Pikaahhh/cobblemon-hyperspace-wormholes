package dev.hyperspacewormholes.wormholes.client.cinematic;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.client.render.WarpTextures;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import static dev.hyperspacewormholes.wormholes.client.cinematic.Fx.ramp;
import static dev.hyperspacewormholes.wormholes.client.cinematic.Fx.smooth;
import static dev.hyperspacewormholes.wormholes.client.cinematic.Fx.tint;
import static dev.hyperspacewormholes.wormholes.client.cinematic.Fx.window;

/**
 * The light of Ultra Necrozma's power-up (NecrozmaSceneCinematic POWER_UP), timed to the frames of Ultra Sun's battle sequence a/0/8/8 1221 and drawn after the game's own: the body turning white-hot under the aura model with coloured bokeh and gold streaks, a yellow-white screen flash over a rainbow wheel, a black screen with a cross flare, teal and cyan screens with growing lights, white rays as the camera pulls back, gold streams circling the body to the end, sparkles, the game's two camera shakes and its sounds on their cues (played flat, from Move FX's render of them).
 * The aura that stays on Ultra Necrozma is UltraAura's.
 */
final class PowerUpEffects {
    static final int AURA_ON = 266;
    private static final int BOKEH = 46, RAYS = 40, STREAMS = 10, SPARKLES = 40;
    private static final int[] BOKEH_COLOURS = {0xFF58C8, 0x9CFF6A, 0xFFE45A, 0xFF9A3C, 0x8CF4FF, 0xFFFFFF};
    private static final int[] WHEEL_COLOURS = {0xFF4FA0, 0xFF8A2E, 0xFFE040, 0xC24FFF, 0xFF4F6A, 0xFFC040};
    private static float[][] bokeh = new float[0][], rays = new float[0][], streams = new float[0][], sparkles = new float[0][];
    private static float size = 1;
    private static int lastSound = -1;

    private PowerUpEffects() {}

    static void start(float sizeFactor) {
        size = sizeFactor;
        lastSound = -1;
        RandomSource random = RandomSource.create(1221);
        bokeh = new float[BOKEH][];
        for (int i = 0; i < BOKEH; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            int c = BOKEH_COLOURS[random.nextInt(BOKEH_COLOURS.length)];
            bokeh[i] = new float[]{(float) Math.cos(a), (float) (random.nextDouble() * 1.4 - 0.4), (float) Math.sin(a),
                    215 + random.nextFloat() * 38, 14 + random.nextFloat() * 16, 0.25f + random.nextFloat() * 0.55f,
                    (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, 2 + random.nextFloat() * 5};
        }
        rays = new float[RAYS][];
        for (int i = 0; i < RAYS; i++) {
            double a = Mth.TWO_PI * i / RAYS + random.nextDouble() * 0.12;
            rays[i] = new float[]{(float) a, 0.6f + random.nextFloat() * 0.8f, 0.04f + random.nextFloat() * 0.08f, random.nextFloat() * 6};
        }
        streams = new float[STREAMS][];
        for (int i = 0; i < STREAMS; i++) {
            streams[i] = new float[]{random.nextFloat() * Mth.TWO_PI, (random.nextFloat() - 0.5f) * 1.2f,
                    0.05f + random.nextFloat() * 0.05f, 0.7f + random.nextFloat() * 0.6f, random.nextFloat() * 0.8f + 0.2f};
        }
        sparkles = new float[SPARKLES][];
        for (int i = 0; i < SPARKLES; i++) {
            sparkles[i] = new float[]{(float) random.nextGaussian(), (float) random.nextGaussian(), (float) random.nextGaussian(),
                    269 + random.nextFloat() * 120, 10 + random.nextFloat() * 14};
        }
    }

    static Vec3 shake(double f) {
        double amp = 0;
        if (f >= 220 && f < 260) amp = 0.06 * (1 - ramp(f, 240, 260));
        if (f >= 280 && f < 350) amp = Math.max(amp, 0.16 * (1 - ramp(f, 300, 350)));
        if (amp <= 0) return Vec3.ZERO;
        return new Vec3(Math.sin(f * 2.1) * amp, Math.sin(f * 2.9 + 1) * amp * 0.7, Math.cos(f * 2.4) * amp).scale(size);
    }

    static void tick(double f) {
        int frame = (int) f;
        cue(frame, 222, "c03a5", 1f, 1f);
        cue(frame, 239, "c0396", 1f, 1f);
        cue(frame, 269, "c03b6", 1f, 1f);
        cue(frame, 279, "c03e1", 1f, 1f);
        cue(frame, 284, "c0278.28fc6770", 0.4724f, 1f);
        cue(frame, 295, "c0277.2a1d088b", 0.5512f, 1f);
    }

    private static void cue(int frame, int at, String sound, float volume, float pitch) {
        if (lastSound >= at || frame < at) return;
        lastSound = at;
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(Wormholes.id("usum.se." + sound),
                SoundSource.NEUTRAL, volume, pitch, SoundInstance.createUnseededRandom(), false, 0,
                SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    static void collect(WorldRenderContext context, double f) {
        GlowQuads q = new GlowQuads(context);
        Vec3 body = NecrozmaSceneCinematic.body(f);
        Vec3 chest = body.add(0, 1.6 * size, 0);
        float hot = window(f, 205, 250, 262, 272);
        if (hot > 0) {
            q.billboard(chest, 4.5 * size, 1f, 0.98f, 0.9f, 0.55f * hot);
            q.billboard(chest, 9 * size, 1f, 0.9f, 0.7f, 0.25f * hot);
        }
        for (float[] b : bokeh) {
            double life = (f - b[3]) / b[4];
            if (life < 0 || life > 1) continue;
            double out = (1.5 + life * 9 * b[5]) * size;
            Vec3 at = chest.add(b[0] * out, (b[1] * 3 + life * 2) * size, b[2] * out);
            float a = (float) Math.sin(life * Math.PI) * 0.8f;
            q.billboard(at, b[9] * 0.09 * size, b[6], b[7], b[8], a);
        }
        float streak = window(f, 222, 236, 255, 266);
        if (streak > 0) {
            for (float[] r : rays) {
                Vec3 dir = q.right().scale(Math.cos(r[0])).add(q.up().scale(Math.sin(r[0])));
                q.ray(chest.add(dir.scale(2 * size)), chest.add(dir.scale((7 + r[1] * 8) * size)), r[2] * 3 * size, 1f, 0.86f, 0.35f,
                        0.6f * streak, 0);
            }
        }
        float burst = window(f, 275, 278, 296, 312);
        if (burst > 0) {
            double grow = smooth(f, 275, 296);
            for (float[] r : rays) {
                double a = r[0] + r[3] * 0.02;
                Vec3 dir = q.right().scale(Math.cos(a)).add(q.up().scale(Math.sin(a)));
                double len = (8 + r[1] * 22) * (0.3 + 0.7 * grow) * size;
                q.ray(chest, chest.add(dir.scale(len)), r[2] * 4 * size, 0.9f, 1f, 0.92f, 0.85f * burst, 0);
            }
            q.billboard(chest, 7 * size, 0.6f, 1f, 0.85f, 0.6f * burst);
        }
        float swirl = window(f, 282, 300, 395, 409);
        if (swirl > 0) {
            Vec3[] trail = new Vec3[14];
            for (float[] s : streams) {
                for (int t = 0; t < trail.length; t++) {
                    double a = s[0] + (f - t * 1.3) * s[2] * (s[1] > 0 ? 1 : -1);
                    double r = (3.2 + 1.4 * s[3]) * size;
                    double h = (s[1] * 3 + Math.sin((f - t * 1.3) * 0.05 + s[0]) * 1.5) * size;
                    trail[t] = body.add(Math.cos(a) * r, 1.5 * size + h, Math.sin(a) * r);
                }
                q.ribbon(trail, 0.35 * s[4] * size, 1f, 0.84f, 0.36f, 0.55f * swirl);
            }
            for (float[] s : sparkles) {
                double life = (f - s[3]) / s[4];
                if (life < 0 || life > 1) continue;
                Vec3 at = body.add(s[0] * 3.5 * size, (1.5 + s[1] * 2.5 + life) * size, s[2] * 3.5 * size);
                q.billboard(at, 0.18 * size, 1f, 0.95f, 0.6f, (float) Math.sin(life * Math.PI));
            }
        }
        q.draw();
    }

    static void screen(GuiGraphics g, double f, int w, int h) {
        int cx = w / 2, cy = h / 2;
        tint(g, w, h, 1 - ramp(f, 0, 8), 0x000000);
        tint(g, w, h, 0.25f * window(f, 181, 190, 200, 206), 0x000000);
        tint(g, w, h, 0.18f * window(f, 220, 222, 224, 230) + 0.22f * window(f, 237, 239, 241, 248), 0xFFF4D0);
        float wheel = window(f, 254, 258, 264, 268);
        if (wheel > 0) {
            for (int i = 0; i < WHEEL_COLOURS.length; i++) {
                double a = Mth.TWO_PI * i / WHEEL_COLOURS.length + f * 0.05;
                glow(g, cx + (int) (Math.cos(a) * h * 0.45), cy + (int) (Math.sin(a) * h * 0.45), h * 0.7f, WHEEL_COLOURS[i], 0.7f * wheel);
            }
        }
        tint(g, w, h, 0.9f * window(f, 246, 252, 256, 262), 0xFFF6B8);
        float dark = window(f, 265, 266, 269, 270);
        if (dark > 0) {
            tint(g, w, h, 0.92f * dark, 0x05060A);
            for (int k = 0; k < 4; k++) {
                int dx = k % 2 == 0 ? 1 : -1, dy = k < 2 ? 1 : -1;
                for (int i = 0; i < 48; i++) {
                    float t = i / 48f;
                    int px = cx + (int) (dx * t * w * 0.42), py = cy + (int) (dy * t * w * 0.24);
                    int half = Math.max(1, (int) ((1 - t) * h * 0.012));
                    g.fill(px - half, py - half, px + half, py + half, Fx.argb(dark * (1 - t), 0xFFF8E8));
                }
            }
            glow(g, cx, cy, h * 0.25f, 0xFFFFFF, dark);
        }
        float teal = window(f, 268, 269, 271, 273);
        if (teal > 0) {
            tint(g, w, h, 0.85f * teal, 0x0E3A36);
            glow(g, cx, cy, (float) (h * (0.15 + 0.35 * ramp(f, 268, 273))), 0xEAFFFF, teal);
        }
        float cyan = window(f, 271, 272, 276, 278);
        if (cyan > 0) {
            tint(g, w, h, 0.85f * cyan, 0x1EB8F0);
            glow(g, cx, cy, h * 0.75f, 0xFFF080, cyan);
            glow(g, cx, cy, h * 0.42f, 0xFFFDE0, cyan);
        }
        tint(g, w, h, 0.35f * window(f, 276, 277, 282, 292), 0xC8FFE8);
        tint(g, w, h, ramp(f, 401, 409), 0x000000);
    }

    private static void glow(GuiGraphics g, int cx, int cy, float size, int rgb, float alpha) {
        if (alpha <= 0.002f) return;
        int s = Math.max(2, (int) size);
        WarpTextures.ensureReady();
        RenderSystem.enableBlend();
        g.setColor((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, Mth.clamp(alpha, 0, 1));
        g.blit(WarpTextures.GLOW, cx - s / 2, cy - s / 2, s, s, 0, 0, 16, 16, 16, 16);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
