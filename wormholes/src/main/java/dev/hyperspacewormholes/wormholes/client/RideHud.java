package dev.hyperspacewormholes.wormholes.client;

import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.ride.Stamina;
import dev.hyperspacewormholes.wormholes.ride.TunnelLayout;
import dev.hyperspacewormholes.wormholes.ride.WarpTunnel;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The ride HUD: distance (light-years in the game's colours, km otherwise), stamina bar, hint and vignette. */
public final class RideHud {
    private static final ResourceLocation BAR_BACKGROUND = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
    private static final ResourceLocation BAR_PROGRESS = Wormholes.id("hud/stamina_bar_progress");
    private static final int BAR_WIDTH = 182;
    private static final int BLINK_TICKS = 5;
    private static final int[] NOTCHES = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 101, 111, 121, 131, 141, 151, 161, 171, 182};

    private RideHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!RideClient.isActive() || mc.options.hideGui) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        float arrival = RideClient.arrival(pt);
        if (arrival <= 0.04f) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        int band = h / 5;
        int shade = (int) (0xB0 * arrival) << 24 | 0x060212;
        g.fillGradient(0, 0, w, band, shade, 0x00060212);
        g.fillGradient(0, h - band, w, h, 0x00060212, shade);

        Font font = mc.font;
        int titleAlpha = (int) (arrival * 255);
        if (titleAlpha > 8) {
            double warp = RideClient.warpDistance(pt);
            TunnelLayout layout = RideClient.layout();
            Component label = Component.translatable("hyperspace_wormholes.hud.distance_title");
            Component value = layout.gameColours() && !layout.isFixed()
                    ? Component.translatable("hyperspace_wormholes.hud.light_years",
                            String.format("%,d", (long) WarpTunnel.lightYears(warp)))
                    : Component.translatable("hyperspace_wormholes.hud.distance", String.format("%,.2f", warp / 1000.0));
            g.drawString(font, label, w - 8 - font.width(label), 10, (titleAlpha << 24) | 0xFFE9A8);
            g.pose().pushPose();
            g.pose().translate(w - 8, 22, 0);
            g.pose().scale(1.5f, 1.5f, 1f);
            g.drawString(font, value, -font.width(value), 0, (titleAlpha << 24) | 0xB8C8FF);
            g.pose().popPose();
        }

        g.drawString(font, Component.translatable("hyperspace_wormholes.hud.controls"), 6, h - 12, (int) (0xC0 * arrival) << 24 | 0xFFFFFF);
        renderStamina(g, w, h, (int) ((1 - arrival) * 16));
    }

    private static void renderStamina(GuiGraphics g, int w, int h, int below) {
        int x = (w - BAR_WIDTH) / 2;
        int y = h - 10 + below;
        int halves = RideClient.staminaHalves();
        int full = Math.min(halves / 2, Stamina.SECTIONS);
        int fill = NOTCHES[full];
        if (halves % 2 == 1 && full < Stamina.SECTIONS) fill += (NOTCHES[full + 1] - NOTCHES[full]) / 2;
        int untilDrain = Stamina.DRAIN_INTERVAL_TICKS
                - Math.max(0, (int) RideClient.time(0) - WarpTunnel.OPENING_TICKS) % Stamina.DRAIN_INTERVAL_TICKS;
        if (full > 0 && halves % 2 == 0 && !RideClient.exhausted() && untilDrain <= BLINK_TICKS * 6
                && (untilDrain - 1) / BLINK_TICKS % 2 == 1) {
            fill = NOTCHES[full - 1];
        }

        g.blitSprite(BAR_BACKGROUND, x, y, BAR_WIDTH, 5);
        if (fill > 0) g.blitSprite(BAR_PROGRESS, BAR_WIDTH, 5, 0, 0, x, y, fill, 5);
    }
}
