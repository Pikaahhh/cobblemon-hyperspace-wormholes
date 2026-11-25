package dev.hyperspacewormholes.hyperspace.mixin;

import dev.hyperspacewormholes.hyperspace.HyperspaceGrey;
import java.util.function.Predicate;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds particles back from the greyscale pass so they get drawn in colour afterwards. The level renderer draws them
 * through the frustum and render type version of render, which the short one also ends up in.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$colourLater(LightTexture lightTexture, Camera camera, float partialTick, Frustum frustum,
                                                  Predicate<ParticleRenderType> types, CallbackInfo ci) {
        if (HyperspaceGrey.holdParticles()) ci.cancel();
    }
}
