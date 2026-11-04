package dev.hyperspacewormholes.wormholes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.wormholes.client.SilhouetteBuffers;
import dev.hyperspacewormholes.wormholes.ride.WildsShadows;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Draws an Ultra Space Wilds Pokemon that is still hidden (WildsShadows) as a black shape: its renderer gets buffers
 * that take away every vertex's colour (SilhouetteBuffers).
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class ShadowSilhouetteMixin {
    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            index = 4)
    private MultiBufferSource hyperspace_wormholes$silhouette(Entity entity, float yaw, float partialTick, PoseStack poseStack,
                                                           MultiBufferSource buffers, int light) {
        return WildsShadows.isShadow(entity) ? new SilhouetteBuffers(buffers) : buffers;
    }
}
