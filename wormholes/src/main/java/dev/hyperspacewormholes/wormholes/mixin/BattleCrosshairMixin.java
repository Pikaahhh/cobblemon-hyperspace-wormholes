package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.client.battlecamera.BattleCamera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves out the crosshair while the battle camera is in use. Optional (require 0).
 */
@Mixin(Gui.class)
public abstract class BattleCrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void hyperspace_wormholes$noCrosshair(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (BattleCamera.active()) ci.cancel();
    }
}
