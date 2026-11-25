package dev.hyperspacewormholes.wormholes.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * The pixel art UltraTunnel draws with beside WarpTextures', painted in code the first time it's needed: SMOKE is opaque
 * (what's painted is brightness), ARCS has its shape in alpha and PLAIN is plain white.
 */
final class UltraTunnelTextures {
    static final ResourceLocation SMOKE = Wormholes.id("dynamic/tunnel_smoke");
    static final ResourceLocation ARCS = Wormholes.id("dynamic/tunnel_arcs");
    static final ResourceLocation PLAIN = Wormholes.id("dynamic/tunnel_plain");
    private static boolean ready;

    private UltraTunnelTextures() {}

    static void ensureReady() {
        if (ready) return;
        ready = true;
        register(SMOKE, smoke());
        register(ARCS, arcs());
        NativeImage plain = new NativeImage(1, 1, false);
        put(plain, 0, 0, 1, 1);
        register(PLAIN, plain);
    }

    private static void register(ResourceLocation id, NativeImage image) {
        DynamicTexture texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        texture.setFilter(false, false);
    }

    private static void put(NativeImage img, int x, int y, float grey, float alpha) {
        int a = Mth.clamp((int) (alpha * 255f + 0.5f), 0, 255);
        int g = Mth.clamp((int) (grey * 255f + 0.5f), 0, 255);
        img.setPixelRGBA(x, y, a << 24 | g << 16 | g << 8 | g);
    }

    private static float step(float v, int steps) {
        return Mth.clamp(Math.round(v * steps), 0, steps) / (float) steps;
    }

    private static NativeImage smoke() {
        int w = 192, h = 128;
        NativeImage img = new NativeImage(w, h, false);
        Field broad = new Field(11, 3, 3), strands = new Field(29, 3, 14), tears = new Field(53, 10, 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float u = x / (float) w, v = y / (float) h;
                float bend = 0.2f * (broad.sample(u, v) - 0.5f);
                float cloud = broad.sample(u + 0.3f, v + bend), strand = strands.sample(u, v + bend);
                float b = 0.2f + 0.7f * cloud * cloud + 1.1f * Math.max(0, strand - 0.46f) * (0.3f + cloud)
                        - 0.35f * Math.max(0, tears.sample(u, v + bend * 2) - 0.55f);
                put(img, x, y, Mth.clamp(step(b, 14), 0.1f, 1), 1);
            }
        }
        return img;
    }

    private static NativeImage arcs() {
        int w = 96, h = 64;
        NativeImage img = new NativeImage(w, h, false);
        Random random = new Random(19);
        float[] alpha = new float[w * h];
        for (int i = 0; i < 58; i++) {
            int x = random.nextInt(w), y = random.nextInt(h), across = 8 + random.nextInt(26), thick = 1 + random.nextInt(2);
            float a = 0.2f + 0.2f * random.nextInt(4);
            int slope = 5 + random.nextInt(8);
            for (int k = 0; k < across; k++) {
                int edge = Math.min(k, across - 1 - k);
                for (int t = 0; t < (edge < 3 ? 1 : thick); t++) {
                    int p = Math.floorMod(y + k / slope + t, h) * w + Math.floorMod(x + k, w);
                    alpha[p] = Math.max(alpha[p], edge < 2 ? a * 0.5f : a);
                }
            }
        }
        for (int i = 0; i < alpha.length; i++) put(img, i % w, i / w, 1, alpha[i]);
        return img;
    }

    private static final class Field {
        private final float[][][] octaves = new float[3][][];

        Field(long seed, int across, int down) {
            Random random = new Random(seed);
            for (int o = 0; o < octaves.length; o++) {
                octaves[o] = new float[down << o][across << o];
                for (float[] row : octaves[o]) for (int i = 0; i < row.length; i++) row[i] = random.nextFloat();
            }
        }

        float sample(float u, float v) {
            float total = 0, weight = 0, amp = 1;
            for (float[][] g : octaves) {
                int h = g.length, w = g[0].length;
                float fx = u * w, fy = v * h;
                int x0 = Mth.floor(fx), y0 = Mth.floor(fy);
                float tx = fx - x0, ty = fy - y0;
                tx *= tx * (3 - 2 * tx);
                ty *= ty * (3 - 2 * ty);
                float a = g[Math.floorMod(y0, h)][Math.floorMod(x0, w)], b = g[Math.floorMod(y0, h)][Math.floorMod(x0 + 1, w)];
                float c = g[Math.floorMod(y0 + 1, h)][Math.floorMod(x0, w)], d = g[Math.floorMod(y0 + 1, h)][Math.floorMod(x0 + 1, w)];
                total += Mth.lerp(ty, Mth.lerp(tx, a, b), Mth.lerp(tx, c, d)) * amp;
                weight += amp;
                amp *= 0.5f;
            }
            return total / weight;
        }
    }
}
