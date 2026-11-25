package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.client.RideCamera;
import dev.hyperspacewormholes.wormholes.client.RideClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Ultra Warp Ride is seen at RideCamera's FOV whatever the player's setting. The hand's own FOV is left alone. */
@Mixin(GameRenderer.class)
public abstract class RideFovMixin {
    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$rideFov(Camera camera, float partialTick, boolean useFovSetting,
                                            CallbackInfoReturnable<Double> cir) {
        if (useFovSetting && RideClient.isActive()) cir.setReturnValue((double) RideCamera.fov(partialTick));
    }
}
