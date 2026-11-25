package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.client.gui.PartyOverlay;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.hyperspacewormholes.wormholes.client.RideClient;
import dev.hyperspacewormholes.wormholes.client.RiderArrivalClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides Cobblemon's party list during the ride, and slides it back in after it. */
@Mixin(PartyOverlay.class)
public abstract class PartyOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$hideForRide(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (RideClient.isActive()) ci.cancel();
    }

    @WrapMethod(method = "render")
    private void hyperspace_wormholes$slideBack(GuiGraphics graphics, DeltaTracker delta, Operation<Void> original) {
        float away = RiderArrivalClient.hudAway(delta.getGameTimeDeltaPartialTick(false));
        graphics.pose().pushPose();
        graphics.pose().translate(away * -100f, 0f, 0f);
        original.call(graphics, delta);
        graphics.pose().popPose();
    }
}
