package dev.hyperspacewormholes.wormholes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.wormholes.client.RiderArrivalClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws riders tiny at a wormhole's mouth and growing as they glide out, and never culls them for being away from
 * their hitbox.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityEmergenceRenderMixin {
    @Unique
    private boolean hyperspace_wormholes$pushed;

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$neverCullEmerging(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (RiderArrivalClient.isEmerging(entity)) cir.setReturnValue(true);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void hyperspace_wormholes$emerge(Entity entity, double x, double y, double z, float yaw, float partialTick,
                                          PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        hyperspace_wormholes$pushed = RiderArrivalClient.transform(entity, x, y, z, partialTick, poseStack);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void hyperspace_wormholes$emergeDone(Entity entity, double x, double y, double z, float yaw, float partialTick,
                                              PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!hyperspace_wormholes$pushed) return;
        hyperspace_wormholes$pushed = false;
        poseStack.popPose();
    }
}
