package dev.hyperspacewormholes.wormholes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.wormholes.client.cinematic.Cinematics;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While one of Necrozma's cutscenes plays (Cinematics), the world is drawn through the scene's own projection only: its
 * field of view replaces what GameRenderer.getFov works out, and this drops what GameRenderer.renderLevel adds on top
 * (view bobbing, hurt tilt, nausea and portal warp), so the shots frame the same at any FOV setting and in any state.
 */
@Mixin(GameRenderer.class)
public abstract class CinematicProjectionMixin {
    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$cinematicFov(Camera camera, float partialTick, boolean world, CallbackInfoReturnable<Double> cir) {
        double[] view = world ? Cinematics.camera(partialTick) : null;
        if (view != null) cir.setReturnValue(view[6]);
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$cinematicNoHurtTilt(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (Cinematics.isPlaying()) ci.cancel();
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$cinematicNoBob(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (Cinematics.isPlaying()) ci.cancel();
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void hyperspace_wormholes$cinematicNoWarp(DeltaTracker delta, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!Cinematics.isPlaying() || player == null) return;
        player.spinningEffectIntensity = 0;
        player.oSpinningEffectIntensity = 0;
    }
}
