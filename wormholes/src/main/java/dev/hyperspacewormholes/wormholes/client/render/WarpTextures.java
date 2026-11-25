package dev.hyperspacewormholes.wormholes.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * Pixel-art textures made in code the first time they're needed (glows, clouds, stars, the opening corridor's wall, the
 * wormhole funnel grid and rings), greyscale with the shape in alpha so they can be tinted, but for the corridor's. The
 * funnel grid keeps only pixels wholly inside the funnel's mesh (FUNNEL_SEGMENTS sides, open at the throat), so its rim
 * is a stepped circle of whole pixels.
 */
public final class WarpTextures {
    public static final ResourceLocation GLOW = Wormholes.id("dynamic/glow");
    public static final ResourceLocation CLOUD = Wormholes.id("dynamic/cloud");
    public static final ResourceLocation STARFIELD = Wormholes.id("dynamic/starfield");
    public static final ResourceLocation FUNNEL = Wormholes.id("dynamic/funnel");
    public static final int FUNNEL_FRAMES = 12;
    public static final float FUNNEL_THROAT = 0.16f / 1.7f;
    public static final int FUNNEL_SEGMENTS = 32;
    public static final ResourceLocation RING = Wormholes.id("dynamic/ring");
    public static final float RING_RADIUS = 0.9f;
    public static final ResourceLocation RING_GLOW = Wormholes.id("dynamic/ring_glow");
    public static final ResourceLocation THIN_RING = Wormholes.id("dynamic/thin_ring");
    public static final ResourceLocation THIN_RING_GLOW = Wormholes.id("dynamic/thin_ring_glow");
    public static final ResourceLocation STAR = Wormholes.id("dynamic/star");
    public static final ResourceLocation WALL = Wormholes.id("dynamic/wall");
    public static final ResourceLocation RUSH_WALL = Wormholes.id("dynamic/rush_wall");
    public static final ResourceLocation BANDS = Wormholes.id("dynamic/bands");
    public static final ResourceLocation CURTAINS = Wormholes.id("dynamic/curtains");
    public static final ResourceLocation SPARKLES = Wormholes.id("dynamic/sparkles");
    public static final ResourceLocation SMOKE = Wormholes.id("dynamic/smoke");

    private static boolean ready;

    private interface Alpha {
        float at(int x, int y);
    }

    public static void ensureReady() {
        if (ready) return;
        ready = true;
        register(GLOW, glow(16));
        register(CLOUD, cloud(64));
        register(STARFIELD, starfield(64));
        register(STAR, star());
        register(FUNNEL, funnel(48, FUNNEL_FRAMES));
        register(RING, ring(48));
        register(RING_GLOW, ringGlow(48, 0.2f));
        register(THIN_RING, ring(96));
        register(THIN_RING_GLOW, ringGlow(96, 0.1f));
        register(WALL, wall(RideOpeningRenderer.COLUMNS, RideOpeningRenderer.ROWS, 0.7f, 0.94f));
        register(RUSH_WALL, wall(RideOpeningRenderer.COLUMNS, RideOpeningRenderer.ROWS, 0.25f, 1.04f));
        register(BANDS, bands(20));
        register(CURTAINS, curtains(RideOpeningRenderer.COLUMNS * 2, 64));
        register(SPARKLES, sparkles(RideOpeningRenderer.COLUMNS * 2));
        register(SMOKE, smoke(RideOpeningRenderer.COLUMNS, 64));
    }

    private static void register(ResourceLocation id, NativeImage image) {
        DynamicTexture texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        texture.setFilter(false, false);
    }

    private static float step(float v, int steps) {
        return Mth.clamp(Math.round(v * steps), 0, steps) / (float) steps;
    }

    private static void put(NativeImage img, int x, int y, float grey, float alpha) {
        int a = Mth.clamp((int) (alpha * 255f), 0, 255);
        int g = Mth.clamp((int) (grey * 255f), 0, 255);
        pixel(img, x, y, (a << 24) | (g << 16) | (g << 8) | g);
    }

    private static void pixel(NativeImage img, int x, int y, int abgr) {
        img.setPixelRGBA(x, y, abgr);
    }

    private static float radius(int x, int y, int size) {
        float dx = (x + 0.5f) / size * 2 - 1;
        float dy = (y + 0.5f) / size * 2 - 1;
        return Mth.sqrt(dx * dx + dy * dy);
    }

    private static NativeImage white(int size, Alpha alpha) {
        NativeImage img = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) put(img, x, y, 1, alpha.at(x, y));
        }
        return img;
    }

    private static NativeImage glow(int size) {
        return white(size, (x, y) -> {
            float r = radius(x, y, size);
            return step(Math.min(r >= 1 ? 0 : (1 - r) * (1 - r) * 1.3f, 1), 4);
        });
    }

    private static NativeImage cloud(int size) {
        NativeImage img = new NativeImage(size, size, false);
        Noise shape = new Noise(23, 4);
        Noise shade = new Noise(41, 8);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float u = (float) x / size, v = (float) y / size;
                float alpha = step((float) Math.pow(shape.sample(u, v), 1.4) * 1.7f - 0.15f, 5);
                put(img, x, y, 0.5f + 0.5f * step(shade.sample(u, v), 3), alpha);
            }
        }
        return img;
    }

    private static NativeImage starfield(int size) {
        NativeImage img = white(size, (x, y) -> 0);
        Random random = new Random(99);
        for (int i = 0; i < 70; i++) {
            int x = random.nextInt(size), y = random.nextInt(size);
            put(img, x, y, 1, i < 40 ? 1f : 0.5f);
            if (i < 6) {
                put(img, (x + 1) % size, y, 1, 0.45f);
                put(img, (x + size - 1) % size, y, 1, 0.45f);
                put(img, x, (y + 1) % size, 1, 0.45f);
                put(img, x, (y + size - 1) % size, 1, 0.45f);
            }
        }
        return img;
    }

    private static NativeImage funnel(int size, int frames) {
        int rings = 7, spokes = 16;
        double twist = 0.12, minGap = 3;
        NativeImage img = new NativeImage(size, size * frames, false);
        double half = size / 2.0;
        double edge = Math.cos(Math.PI / FUNNEL_SEGMENTS);
        double logSpan = Math.log(1 / FUNNEL_THROAT);
        double ringRatio = Math.exp(logSpan / rings);
        for (int f = 0; f < frames; f++) {
            double phase = (double) f / frames;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    double cx = x + 0.5 - half, cy = y + 0.5 - half;
                    double rpx = Math.sqrt(cx * cx + cy * cy);
                    double r = rpx / half;
                    double far = Math.hypot(Math.abs(cx) + 0.5, Math.abs(cy) + 0.5) / half;
                    double near = Math.hypot(Math.max(0, Math.abs(cx) - 0.5), Math.max(0, Math.abs(cy) - 0.5)) / half;
                    float alpha = 0;
                    if (far <= edge && near >= FUNNEL_THROAT) {
                        double ringCoord = Math.log(r / FUNNEL_THROAT) / logSpan * rings + phase;
                        long k = Math.round(ringCoord);
                        double ringPx = FUNNEL_THROAT * half * Math.exp((k - phase) * logSpan / rings);
                        double ringGap = ringPx * (ringRatio - 1);
                        boolean ring = Math.abs(rpx - ringPx) <= 0.5 && ringGap >= minGap;
                        double spokeCoord = Math.atan2(cy, cx) / (Math.PI * 2) * spokes + ringCoord * twist + phase;
                        double offset = (spokeCoord - Math.round(spokeCoord)) / spokes * Math.PI * 2 * rpx;
                        boolean spoke = Math.abs(offset) <= 0.5 && Math.PI * 2 * rpx / spokes >= minGap;
                        alpha = ring || spoke ? 1f : ringGap < minGap ? 0.55f : 0.14f;
                    }
                    put(img, x, f * size + y, 1, alpha);
                }
            }
        }
        return img;
    }

    private static NativeImage wall(int width, int height, float near, float bright) {
        Noise edges = new Noise(31, 8), patches = new Noise(77, 16), shades = new Noise(5, 24);
        Random random = new Random(808);
        NativeImage img = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float u = (float) x / width, v = (float) y / height, s = (y + 0.5f) / height;
                float[] rgb = flat(s + (edges.sample(u, v) - 0.5f) * 0.1f);
                float patch = patches.sample(u, v) - 0.5f;
                if (patch > 0) {
                    float[] other = flat(s >= 0.3f ? s + 0.64f : s < 0.06f ? 0.13f : 0f);
                    float much = (s > 0.74f ? 0.6f : s < 0.24f ? near : 0.2f) * (patch > 0.07f ? 1f : 0.5f);
                    for (int k = 0; k < 3; k++) rgb[k] = Mth.lerp(much, rgb[k], other[k]);
                }
                float light = bright + 0.05f * Mth.clamp((int) (shades.sample(u, v) * 5) - 1, 0, 2)
                        + 0.015f * (random.nextInt(3) - 1);
                pixel(img, x, y, 0xFF000000 | channel(rgb[2] * light) << 16 | channel(rgb[1] * light) << 8
                        | channel(rgb[0] * light));
            }
        }
        return img;
    }

    private static float[] flat(float s) {
        return RideOpeningRenderer.wall((Mth.floor(s * 20) + 0.5f) / 20f);
    }

    private static int channel(float v) {
        return Mth.clamp((int) (v * 255f), 0, 255);
    }

    private static NativeImage bands(int rows) {
        return white(rows, (x, y) -> y == 0 ? 1f : y <= 2 ? 0.5f : 0f);
    }

    private static NativeImage curtains(int width, int height) {
        Noise strands = new Noise(5, 8), folds = new Noise(61, 4);
        NativeImage img = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float u = (float) x / width * 2, v = (float) y / height;
                float strand = Mth.clamp((strands.sample(u, v * 2) - 0.42f) * 2.4f, 0, 1);
                float fold = Mth.clamp((folds.sample(u * 2, v) - 0.36f) * 3.4f, 0, 1);
                put(img, x, y, 1, step(strand * fold, 3));
            }
        }
        return img;
    }

    private static NativeImage sparkles(int size) {
        int[] colours = {0xFFFFFF, 0xFFD0FF, 0xB0FF60, 0xF060FF, 0x7050FF, 0xFFD050, 0xFF90A0};
        NativeImage img = white(size, (x, y) -> 0);
        Random random = new Random(318);
        for (int i = 0; i < 2400; i++) {
            int x = random.nextInt(size), y = random.nextInt(size), length = 1 + random.nextInt(3);
            int colour = colours[random.nextInt(colours.length)];
            int alpha = 85 * (1 + random.nextInt(3));
            for (int k = 0; k < length; k++) pixel(img, x, (y + k) % size, (alpha << 24) | colour);
        }
        return img;
    }

    private static NativeImage smoke(int width, int height) {
        Noise shape = new Noise(907, 8), shade = new Noise(13, 8);
        NativeImage img = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float u = (float) x / width * 2, v = (float) y / height;
                float alpha = step((shape.sample(u, v) - 0.36f) * 4f, 3);
                put(img, x, y, 0.5f + 0.5f * step(shade.sample(u, v) * 2.2f - 0.6f, 3), alpha);
            }
        }
        return img;
    }

    private static NativeImage ringGlow(int size, float width) {
        return white(size, (x, y) -> {
            float d = Math.abs(radius(x, y, size) - (RING_RADIUS - 1f / size)) / width;
            return step(d >= 1 ? 0 : (1 - d) * (1 - d), 4);
        });
    }

    private static NativeImage ring(int size) {
        float[] radii = {RING_RADIUS, RING_RADIUS - 2f / size};
        return white(size, (x, y) -> {
            boolean on = false;
            for (float target : radii) {
                boolean in = false, out = false;
                for (int corner = 0; corner < 4; corner++) {
                    double cx = (x + (corner & 1)) - size / 2.0, cy = (y + (corner >> 1)) - size / 2.0;
                    double cr = Math.sqrt(cx * cx + cy * cy) / (size / 2.0);
                    if (cr < target) in = true;
                    else out = true;
                }
                on |= in && out;
            }
            return on ? 1f : 0f;
        });
    }

    private static NativeImage star() {
        return white(5, (x, y) -> {
            int dx = Math.abs(x - 2), dy = Math.abs(y - 2);
            return dx == 0 && dy == 0 ? 1f : (dx == 0 || dy == 0) && dx + dy == 1 ? 0.6f : (dx == 0 || dy == 0) ? 0.2f : 0f;
        });
    }

    private static final class Noise {
        private final float[][][] octaves = new float[3][][];
        private final int base;

        Noise(long seed, int base) {
            this.base = base;
            Random random = new Random(seed);
            for (int o = 0; o < octaves.length; o++) {
                int w = base << o;
                octaves[o] = new float[w][w];
                for (int i = 0; i < w; i++) for (int j = 0; j < w; j++) octaves[o][i][j] = random.nextFloat();
            }
        }

        float sample(float u, float v) {
            float total = 0, weight = 0, amp = 1;
            for (int o = 0; o < octaves.length; o++) {
                int w = base << o;
                float fx = u * w, fy = v * w;
                int x0 = Mth.floor(fx), y0 = Mth.floor(fy);
                float tx = smooth(fx - x0), ty = smooth(fy - y0);
                float[][] g = octaves[o];
                float a = g[Math.floorMod(x0, w)][Math.floorMod(y0, w)];
                float b = g[Math.floorMod(x0 + 1, w)][Math.floorMod(y0, w)];
                float c = g[Math.floorMod(x0, w)][Math.floorMod(y0 + 1, w)];
                float d = g[Math.floorMod(x0 + 1, w)][Math.floorMod(y0 + 1, w)];
                total += Mth.lerp(ty, Mth.lerp(tx, a, b), Mth.lerp(tx, c, d)) * amp;
                weight += amp;
                amp *= 0.5f;
            }
            return total / weight;
        }

        private static float smooth(float t) {
            return t * t * (3 - 2 * t);
        }
    }
}
