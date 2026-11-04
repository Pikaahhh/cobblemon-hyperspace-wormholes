package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.client.BurnsSkyClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shakes the camera of players near a Light That Burns the Sky on the game's own shake cues (BurnsSkyClient.shake), by
 * turning it a little once it has been lined up with the player each frame. Optional (require 0), so a camera mod
 * that changes the method only loses the shake.
 */
@Mixin(Camera.class)
public abstract class BurnsSkyShakeMixin {
    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void hyperspace_wormholes$burnsSkyShake(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                                    float partialTick, CallbackInfo ci) {
        float[] shake = BurnsSkyClient.shake(partialTick);
        if (shake == null) return;
        setRotation(yRot + shake[0], xRot + shake[1]);
    }
}
