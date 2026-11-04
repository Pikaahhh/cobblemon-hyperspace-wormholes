package dev.hyperspacewormholes.wormholes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.wormholes.client.battlecamera.BattleCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Leaves out the first-person hand while the battle camera is in use, and the view bobbing and hurt tilt GameRenderer
 * puts on the projection, so the only shake on a battle shot is the move animations' camera_shake, and gives the world
 * the shot's field of view. Optional (require 0).
 */
@Mixin(GameRenderer.class)
public abstract class BattleViewMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true, require = 0)
    private void hyperspace_wormholes$noHand(Camera camera, float partialTick, Matrix4f view, CallbackInfo ci) {
        if (BattleCamera.active()) ci.cancel();
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true, require = 0)
    private void hyperspace_wormholes$noBob(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (BattleCamera.lastShot() != null) ci.cancel();
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void hyperspace_wormholes$noHurtTilt(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (BattleCamera.lastShot() != null) ci.cancel();
    }

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true, require = 0)
    private void hyperspace_wormholes$battleFov(Camera camera, float partialTick, boolean world, CallbackInfoReturnable<Double> cir) {
        BattleCamera.Shot shot = BattleCamera.lastShot();
        if (world && shot != null) cir.setReturnValue((double) shot.fov());
    }
}
