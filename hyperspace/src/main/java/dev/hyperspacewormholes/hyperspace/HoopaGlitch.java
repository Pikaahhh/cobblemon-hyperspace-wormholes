package dev.hyperspacewormholes.hyperspace;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;

/**
 * The task list going wrong as the Hoopa event gives itself away: after HoopaEvent.Glitch, TasksHud asks how many of its
 * characters have gone (eaten) and has bands of torn, colour-split noise drawn over the list (draw). The glitch lingers
 * LINGER_MS after the text goes, and the list stays hidden until the event sends Glitch(0) or you leave Hyperspace.
 */
public final class HoopaGlitch {
    private static final int[] COLOURS = {0xFF00FF, 0x00FFFF, 0x000000, 0x6A00FF, 0xFFFFFF, 0xF3FF00};
    private static final int LINGER_MS = 660;
    private static long until = -1, glitchEnd = -1;
    private static int length;
    private static boolean hidden;

    private HoopaGlitch() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HoopaEvent.Glitch.TYPE, (payload, context) -> {
            HyperspaceMusic.glitch(payload.ticks());
            hidden = false;
            length = Math.max(1, payload.ticks());
            until = payload.ticks() > 0 ? System.currentTimeMillis() + length * 50L : -1;
            glitchEnd = until < 0 ? -1 : until + LINGER_MS;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            until = -1;
            glitchEnd = -1;
            hidden = false;
        });
    }

    static boolean hidden() {
        Minecraft mc = Minecraft.getInstance();
        if (hidden && (mc.level == null || mc.level.dimension() != Hyperspace.HYPERSPACE)) hidden = false;
        if (until >= 0 && System.currentTimeMillis() >= until) {
            until = -1;
            hidden = true;
        }
        return hidden;
    }

    /** How many of this many characters have gone so far, a step at a time, all of them by the glitch's end. */
    static int eaten(int total, int step) {
        if (until < 0) return 0;
        float t = 1 - (until - System.currentTimeMillis()) / (length * 50f);
        return Math.min(total, (int) Math.ceil(total * t / step) * step);
    }

    static void draw(GuiGraphics graphics, int left, int top, int w, int h) {
        long now = System.currentTimeMillis();
        if (now >= glitchEnd || w <= 0 || h <= 0) return;
        float t = 1 - (glitchEnd - now) / (length * 50f + LINGER_MS);
        float strength = (float) Math.sin(Math.PI * t);
        // a new tear every frame
        RandomSource random = RandomSource.create(now / 33);
        graphics.fill(left, top, left + w, top + h, argb(0x1A0033, 0.25f * strength));
        int bands = 4 + (int) (14 * strength);
        for (int i = 0; i < bands; i++) {
            int y = top + random.nextInt(h);
            int height = Math.min(1 + random.nextInt(Math.max(2, h / 14)), top + h - y);
            int shift = (int) ((random.nextFloat() - 0.5f) * w * 0.3f * strength);
            int colour = COLOURS[random.nextInt(COLOURS.length)];
            float alpha = (0.25f + 0.6f * random.nextFloat()) * strength;
            int from = left + Math.max(0, shift), to = left + Math.min(w, w + shift);
            graphics.fill(from, y, to, y + height, argb(colour, alpha));
            // the colour split: a thin offset copy in the opposite hue
            if (y + height < top + h) graphics.fill(from + 3, y + height, Math.min(left + w, to + 3), y + height + 1, argb(colour ^ 0xFFFFFF, alpha * 0.7f));
        }
        for (int i = 0; i < 30 * strength; i++) {
            int x = left + random.nextInt(w), y = top + random.nextInt(h), size = 1 + random.nextInt(4);
            graphics.fill(x, y, Math.min(left + w, x + size * 3), Math.min(top + h, y + size), argb(COLOURS[random.nextInt(COLOURS.length)], 0.8f * strength));
        }
    }

    private static int argb(int rgb, float alpha) {
        return ((int) (Math.min(1f, Math.max(0f, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
