package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.hyperspacewormholes.wormholes.client.RideClient;
import dev.hyperspacewormholes.wormholes.client.render.WarpWhite;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the white wormhole flash over the whole screen, HUD and loading screen included, with any other open screen on
 * top.
 */
@Mixin(GameRenderer.class)
public abstract class FlashOverlayMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER))
    private void hyperspace_wormholes$flashUnderScreens(DeltaTracker delta, boolean renderLevel, CallbackInfo ci,
                                                    @Local GuiGraphics graphics) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && !(screen instanceof ReceivingLevelScreen)) draw(graphics, delta, 0);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V"))
    private void hyperspace_wormholes$flashOverEverything(DeltaTracker delta, boolean renderLevel, CallbackInfo ci,
                                                      @Local GuiGraphics graphics) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null || screen instanceof ReceivingLevelScreen) draw(graphics, delta, 3000);
    }

    private static void draw(GuiGraphics graphics, DeltaTracker delta, float z) {
        float pt = delta.getGameTimeDeltaPartialTick(false);
        if (RideClient.isActive() && WarpWhite.swelling(pt)) {
            float scale = 1f / (float) Minecraft.getInstance().getWindow().getGuiScale();
            int x = WarpWhite.middleX(), y = WarpWhite.middleY(), cell = WarpWhite.cellPixels();
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, z);
            graphics.pose().scale(scale, scale, 1f);
            WarpWhite.draw((x0, y0, x1, y1, part) -> graphics.fill(x + x0 * cell, y - y1 * cell, x + x1 * cell, y - y0 * cell,
                    WarpWhite.colour(part)), pt);
            graphics.pose().popPose();
        }
        float flash = RideClient.flash(pt);
        if (flash <= 0.001f) return;
        int alpha = (int) (Mth.clamp(flash, 0f, 1f) * 255);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, z);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | 0xFFFFFF);
        graphics.pose().popPose();
    }
}
