package dev.hyperspacewormholes.wormholes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.wormholes.client.RideClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tilts the legend and you into turns when you steer sideways during the ride.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityTiltMixin {
    @Inject(method = "setupRotations", at = @At("HEAD"))
    private void hyperspace_wormholes$bank(LivingEntity entity, PoseStack poseStack, float bob, float bodyRot,
                                        float partialTick, float scale, CallbackInfo ci) {
        if (!RideClient.isActive()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Entity mount = player.getVehicle();
        if (entity != player && entity != mount) return;
        float roll = RideClient.roll(partialTick);
        if (Math.abs(roll) < 0.05f) return;

        Entity body = mount != null ? mount : player;
        Vec3 pivot = body.getPosition(partialTick).add(0, body.getBbHeight() * 0.5, 0)
                .subtract(entity.getPosition(partialTick));
        float yaw = RideClient.facingYaw() * Mth.DEG_TO_RAD;
        poseStack.translate(pivot.x, pivot.y, pivot.z);
        poseStack.mulPose(new Quaternionf().rotationAxis(roll * Mth.DEG_TO_RAD, -Mth.sin(yaw), 0, Mth.cos(yaw)));
        poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
    }
}
