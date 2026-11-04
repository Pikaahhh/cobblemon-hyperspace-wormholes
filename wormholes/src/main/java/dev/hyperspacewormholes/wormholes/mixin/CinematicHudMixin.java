package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.client.BossTransition;
import dev.hyperspacewormholes.wormholes.client.cinematic.Cinematics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While one of Necrozma's cutscenes shows, the HUD (and with it Cobblemon's battle overlay, drawn inside it) is replaced
 * by the scene's screen fades and flashes (Cinematics.hud), as the game's cutscenes show no HUD, and while a boss
 * screen transition plays (BossTransition) there is no HUD at all.
 */
@Mixin(Gui.class)
public abstract class CinematicHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$cinematicScreen(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (Cinematics.hud(graphics, delta) || BossTransition.hidesUi()) ci.cancel();
    }
}
