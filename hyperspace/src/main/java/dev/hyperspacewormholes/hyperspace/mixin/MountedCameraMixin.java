package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.client.render.camera.MountedCameraRenderer;
import dev.hyperspacewormholes.hyperspace.RideCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tweaks Cobblemon's ride camera so it fits the mount instead of ending up inside it.
 */
@Mixin(value = MountedCameraRenderer.class, remap = false)
public abstract class MountedCameraMixin {
    @Inject(method = "getCameraPosition", at = @At("RETURN"), cancellable = true)
    private void hyperspace_wormholes$fitMount(Camera camera, Entity driver, Entity vehicle, boolean frontView, double a, double b, CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(RideCamera.fit(camera, vehicle, cir.getReturnValue()));
    }
}
