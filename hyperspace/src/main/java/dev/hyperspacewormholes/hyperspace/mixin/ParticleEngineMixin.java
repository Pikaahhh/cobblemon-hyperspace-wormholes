package dev.hyperspacewormholes.hyperspace.mixin;

import dev.hyperspacewormholes.hyperspace.HyperspaceGrey;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds particles back from the greyscale pass so they get drawn in colour afterwards.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$colourLater(LightTexture lightTexture, Camera camera, float partialTick, CallbackInfo ci) {
        if (HyperspaceGrey.holdParticles()) ci.cancel();
    }
}
