package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.client.gui.PartyOverlay;
import dev.hyperspacewormholes.wormholes.client.RideClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides Cobblemon's party list during the ride. */
@Mixin(PartyOverlay.class)
public abstract class PartyOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$hideForRide(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (RideClient.isActive()) ci.cancel();
    }
}
