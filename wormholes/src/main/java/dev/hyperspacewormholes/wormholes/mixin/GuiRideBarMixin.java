package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.hyperspacewormholes.wormholes.client.RideClient;
import dev.hyperspacewormholes.wormholes.client.RiderArrivalClient;
import dev.hyperspacewormholes.wormholes.client.RideHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the hotbar, hearts, food, XP and crosshair during the ride, and slides them back in after it. */
@Mixin(Gui.class)
public abstract class GuiRideBarMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$hideForEmergence(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!RiderArrivalClient.isWatching()) return;
        RideHud.render(graphics, delta);
        ci.cancel();
    }

    @Inject(method = {"renderHotbarAndDecorations", "renderExperienceLevel", "renderCrosshair"}, at = @At("HEAD"),
            cancellable = true)
    private void hyperspace_wormholes$hideForRide(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (RideClient.isActive()) ci.cancel();
    }

    @WrapMethod(method = "renderHotbarAndDecorations")
    private void hyperspace_wormholes$slideBack(GuiGraphics graphics, DeltaTracker delta, Operation<Void> original) {
        float away = RiderArrivalClient.hudAway(delta.getGameTimeDeltaPartialTick(false));
        graphics.pose().pushPose();
        graphics.pose().translate(0f, away * 60f, 0f);
        original.call(graphics, delta);
        graphics.pose().popPose();
    }
}
