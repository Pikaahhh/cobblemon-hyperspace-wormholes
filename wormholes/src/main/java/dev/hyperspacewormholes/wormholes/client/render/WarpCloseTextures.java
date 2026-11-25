package dev.hyperspacewormholes.wormholes.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * The pixel art WarpCloseLook draws a closing wormhole's light with, painted in code the first time it's needed on one
 * sheet, white with the shape in alpha so it can be tinted.
 */
final class WarpCloseTextures {
    record Sprite(float u0, float v0, float u1, float v1) {}

    private interface Pixel {
        float alpha(int x, int y);
    }

    private interface Polar {
        float alpha(float radius, float angle);
    }

    static final ResourceLocation SHEET = Wormholes.id("dynamic/warp_close");
    static final float RING_AT = 0.82f;
    static final float REACH = 4.6f;
    private static final int SPIRAL = 144;
    /** One pixel of the spirals, in the close's units. */
    static final float PIXEL = 2 * REACH / SPIRAL;
    static final int ARMS = 12;
    static final Sprite[] SPIKES = new Sprite[3], LUMPS = new Sprite[4];
    static Sprite arms, swirl, star, ring, disc, shell, rays, glow, dot;
    private static final int SIZE = 512;
    private static NativeImage sheet;
    private static int penX, penY, rowHeight;

    private WarpCloseTextures() {}

    static void ensureReady() {
        if (dot != null) return;
        sheet = new NativeImage(SIZE, SIZE, true);
        arms = spiral(false);
        swirl = spiral(true);
        rays = rays();
        for (int i = 0; i < SPIKES.length; i++) SPIKES[i] = spikes(new Random(61 + i));
        for (int i = 0; i < LUMPS.length; i++) LUMPS[i] = lumps(new Random(83 + i));
        star = round(64, (r, angle) -> {
            float k = angle / Mth.TWO_PI * 7 + 0.3f, to = Math.abs(k - Math.round(k)) * 2;
            float tip = Math.floorMod(Math.round(k), 2) == 0 ? 0.98f : 0.8f;
            return r < 0.52f + (tip - 0.52f) * (float) Math.pow(1 - to, 4.5) ? 1 : 0;
        });
        ring = round(80, (r, angle) -> {
            float px = (r - RING_AT) * 40;
            return Math.abs(px) < 0.8f ? 1 : px > 0 ? step(0.5f * (1 - px / 6f), 4) : step(0.35f * (1 + px / 3f), 4);
        });
        disc = round(16, (r, angle) -> r < 1 ? 1 : 0);
        shell = round(64, (r, angle) -> r >= 1 ? 0 : step(0.25f + 0.75f * (float) Math.pow(r, 6), 5));
        glow = round(48, (r, angle) -> r >= 1 ? 0 : step((1 - r) * (1 - r) * 1.2f, 6));
        dot = sprite(4, (x, y) -> 1);
        DynamicTexture texture = new DynamicTexture(sheet);
        Minecraft.getInstance().getTextureManager().register(SHEET, texture);
        texture.setFilter(false, false);
        sheet = null;
    }

    private static float step(float v, int steps) {
        return Mth.clamp(Math.round(v * steps), 0, steps) / (float) steps;
    }

    private static Sprite sprite(int size, Pixel pixel) {
        if (penX + size > SIZE) {
            penX = 0;
            penY += rowHeight;
            rowHeight = 0;
        }
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int a = Mth.clamp((int) (pixel.alpha(x, y) * 255f + 0.5f), 0, 255);
                sheet.setPixelRGBA(penX + x, penY + y, a << 24 | 0xFFFFFF);
            }
        }
        float in = 0.02f;
        Sprite sprite = new Sprite((penX + in) / SIZE, (penY + in) / SIZE, (penX + size - in) / SIZE, (penY + size - in) / SIZE);
        penX += size + 1;
        rowHeight = Math.max(rowHeight, size + 1);
        return sprite;
    }

    private static Sprite round(int size, Polar polar) {
        return sprite(size, (x, y) -> {
            float dx = (x + 0.5f) / size * 2 - 1, dy = 1 - (y + 0.5f) / size * 2;
            return polar.alpha(Mth.sqrt(dx * dx + dy * dy), (float) Math.atan2(dy, dx));
        });
    }

    /** ARMS arms that wind anticlockwise outwards, 1.45 radians for every time the radius grows by e. */
    private static Sprite spiral(boolean core) {
        return round(SPIRAL, (r, angle) -> {
            float d = r * REACH;
            if (r >= 1 || d < (core ? 0.25f : 0.95f)) return 0;
            float arm = (angle - 1.45f * (float) Math.log(d)) / Mth.TWO_PI * ARMS;
            int which = Math.floorMod(Math.round(arm), 3);
            float off = (arm - Math.round(arm)) / ARMS * Mth.TWO_PI * d, grow = Math.min(1.5f, 0.3f + 0.35f * d);
            float wide = (which == 0 ? 0.3f : which == 1 ? 0.12f : 0.2f) * grow, alpha;
            if (core) {
                float near = d < 1.2f ? 1 : 0.2f + 0.8f * Math.max(0, 1 - (d - 1.2f) / 0.6f);
                alpha = Math.abs(off) > wide * 0.8f ? 0 : near * (which == 2 ? 0.6f : which == 0 && d > 1.7f ? 0.75f : 0.95f);
                alpha *= Math.min(1, (1 - r) * 2.5f);
            } else {
                boolean edge = Math.abs(off - wide) < 0.06f * grow;
                alpha = edge ? 0.85f : Math.abs(off) < wide ? which == 0 ? 0.32f : 0.55f : Math.abs(off) < wide * 2 ? 0.12f : 0;
                alpha *= Math.min(1, (1 - r) * 1.7f) * Mth.clamp((d - 0.95f) * 2.5f, 0, 1);
            }
            return step(alpha, 8);
        });
    }

    private static float turns(float angle) {
        return angle < 0 ? angle / Mth.TWO_PI + 1 : angle / Mth.TWO_PI;
    }

    private static Sprite spikes(Random random) {
        float[] tall = new float[26];
        for (int i = 0; i < tall.length; i++) tall[i] = 0.62f + 0.4f * random.nextFloat() * random.nextFloat() + (i % 4 == 0 ? 0.18f : 0);
        return round(72, (r, angle) -> {
            float at = turns(angle) * tall.length;
            float top = Math.min(1, tall[(int) at % tall.length]) * (1 - 0.36f * Math.abs(at - (int) at - 0.5f) * 2);
            return r < top ? r < top * 0.8f ? 1 : 0.6f : 0;
        });
    }

    private static Sprite lumps(Random random) {
        float[] at = new float[15];
        for (int i = 0; i < at.length; i++) at[i] = 0.02f + 0.15f * random.nextFloat();
        return round(72, (r, angle) -> {
            float a = turns(angle) * at.length;
            int k = (int) a % at.length;
            float wide = Mth.lerp(a - (int) a, at[k], at[(k + 1) % at.length]), off = r - RING_AT;
            return off < -0.01f || r >= 1 ? 0 : off < wide * 0.6f ? 0.55f : off < wide ? 0.25f : 0;
        });
    }

    private static Sprite rays() {
        int size = 160, half = size / 2, count = 18;
        Random random = new Random(97);
        float[] alpha = new float[size * size];
        for (int i = 0; i < count; i++) {
            double angle = (i + 0.7 * (random.nextDouble() - 0.5)) / count * Math.PI * 2, end = 44 + 36 * random.nextDouble();
            for (double r = 4; r < end; r += 0.4) {
                int x = (int) Math.floor(half + Math.cos(angle) * r), y = (int) Math.floor(half + Math.sin(angle) * r);
                alpha[y * size + x] = Math.max(alpha[y * size + x], step((float) (1 - r / end) * 1.2f, 5));
            }
        }
        return sprite(size, (x, y) -> Math.min(1, alpha[y * size + x]));
    }
}
