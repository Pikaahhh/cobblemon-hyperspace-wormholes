package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.client.BossTransition;
import dev.hyperspacewormholes.wormholes.client.cinematic.Cinematics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No toasts are drawn while one of Necrozma's cutscenes shows or a boss screen transition plays. Toasts are drawn
 * outside the HUD, so CinematicHudMixin misses them.
 * Only the drawing stops: toasts keep their timers and show again after the scene if still up.
 */
@Mixin(ToastComponent.class)
public abstract class CinematicToastMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$hideInCinematic(GuiGraphics graphics, CallbackInfo ci) {
        if (Cinematics.isShowing() || BossTransition.hidesUi()) ci.cancel();
    }
}
