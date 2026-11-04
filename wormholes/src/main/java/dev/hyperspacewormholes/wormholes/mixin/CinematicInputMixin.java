package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.client.cinematic.Cinematics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The player can't walk, jump or sneak while one of Necrozma's cutscenes plays (Cinematics), so they stay where its
 * shots of them are framed.
 */
@Mixin(KeyboardInput.class)
public abstract class CinematicInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void hyperspace_wormholes$holdStill(boolean sneaking, float sneakSpeed, CallbackInfo ci) {
        if (!Cinematics.isPlaying()) return;
        up = down = left = right = jumping = shiftKeyDown = false;
        forwardImpulse = leftImpulse = 0;
    }
}
