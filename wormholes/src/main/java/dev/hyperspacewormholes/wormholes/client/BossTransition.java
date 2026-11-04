package dev.hyperspacewormholes.wormholes.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.hyperspacewormholes.wormholes.network.BossTransitionPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

/**
 * The boss fight's screen transitions (BossTransitionPayload), drawn after everything else on screen (BossTransitionMixin) with the HUD and the battle screen hidden while one plays.
 * Like Emerald's slice transition: two white flashes, then BARS black bars sliding in together, every other one from the opposite edge, until the screen is black; it stays black for the hold and the bars slide back out. The opening one slides rows across, the one before Necrozma's transformation columns up and down.
 * The HUD and battle screen stay hidden for the payload's ui ticks after the bars have gone (the cutscene that follows). Times are client ticks from the payload's arrival.
 */
public final class BossTransition {
    private static final int BARS = 16;

    private static @Nullable BossTransitionPayload playing;
    private static int age;

    private BossTransition() {}

    public static void start(BossTransitionPayload payload) {
        playing = payload;
        age = 0;
    }

    public static void tick(Minecraft mc) {
        if (playing == null) return;
        if (mc.level == null || ++age > playing.length() + playing.ui()) playing = null;
    }

    public static boolean hidesUi() {
        return playing != null;
    }

    public static void draw(GuiGraphics graphics, DeltaTracker delta) {
        BossTransitionPayload p = playing;
        if (p == null) return;
        float t = age + delta.getGameTimeDeltaPartialTick(false);
        if (t >= p.length()) return;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        // Whatever was drawn before sits at some depth; these go over all of it.
        graphics.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        if (t < p.flash()) {
            float phase = t / p.flash() * 4f;
            if ((int) phase % 2 == 0) {
                float a = 1f - Math.abs(phase % 1f * 2f - 1f);
                graphics.fill(0, 0, w, h, ((int) (a * 255) << 24) | 0xFFFFFF);
            }
            return;
        }
        float cover;
        if (t < p.black()) {
            cover = ease((t - p.flash()) / Math.max(1, p.close()));
        } else if (t < p.black() + p.hold()) {
            cover = 1f;
        } else {
            cover = 1f - ease((t - p.black() - p.hold()) / Math.max(1, p.open()));
        }
        if (cover <= 0f) return;
        int across = p.vertical() ? h : w;
        int along = p.vertical() ? w : h;
        int length = Mth.ceil(across * Mth.clamp(cover, 0f, 1f));
        for (int i = 0; i < BARS; i++) {
            int from = along * i / BARS, to = along * (i + 1) / BARS;
            int start = i % 2 == 0 ? 0 : across - length;
            if (p.vertical()) {
                graphics.fill(from, start, to, start + length, 0xFF000000);
            } else {
                graphics.fill(start, from, start + length, to, 0xFF000000);
            }
        }
    }

    private static float ease(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }
}
