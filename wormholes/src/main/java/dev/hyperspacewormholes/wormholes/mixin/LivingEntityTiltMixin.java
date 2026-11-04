package dev.hyperspacewormholes.wormholes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.wormholes.client.RideClient;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tilts the legend and you into turns when you steer sideways during the ride (RideClient.bank).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityTiltMixin {
    @Inject(method = "setupRotations", at = @At("HEAD"))
    private void hyperspace_wormholes$bank(LivingEntity entity, PoseStack poseStack, float bob, float bodyRot,
                                        float partialTick, float scale, CallbackInfo ci) {
        RideClient.bank(entity, poseStack, partialTick);
    }
}
