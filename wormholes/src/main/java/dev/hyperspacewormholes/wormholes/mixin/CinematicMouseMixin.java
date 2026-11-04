package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.client.cinematic.Cinematics;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moving the mouse doesn't turn the player while one of Necrozma's cutscenes plays (Cinematics). */
@Mixin(MouseHandler.class)
public abstract class CinematicMouseMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$noTurning(double delta, CallbackInfo ci) {
        if (Cinematics.isPlaying()) ci.cancel();
    }
}
