package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.wormholes.client.StatueMeshes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Draws the Ultra Plant's statue Xurkitrees from their kept meshes (client/StatueMeshes) instead of having their
 * renderer pose and draw them every frame; the renderer itself fills a mesh, drawn once at the origin into the
 * buffers StatueMeshes hands it.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class StatueMeshMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void hyperspace_wormholes$keptMesh(EntityRenderer<Entity> renderer, Entity entity, float yaw, float partialTick,
                                               PoseStack pose, MultiBufferSource buffers, int light, Operation<Void> original) {
        if (!StatueMeshes.draw(entity, pose, light, source -> original.call(renderer, entity, yaw, partialTick, new PoseStack(), source, light))) {
            original.call(renderer, entity, yaw, partialTick, pose, buffers, light);
        }
    }
}
