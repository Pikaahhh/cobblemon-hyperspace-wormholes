package dev.hyperspacewormholes.wormholes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hyperspacewormholes.wormholes.client.cinematic.NecrozmaSceneCinematic;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the beaten Necrozma a little into the Megalo Tower's floor at the end of the boss fight (NecrozmaSceneCinematic.sink),
 * as a move of the drawn model only, made where Cobblemon's renderer starts posing it so nothing that places the entity
 * can undo it. The pose itself is left as its animation has it. It runs on every client that draws it.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class NecrozmaSinkMixin {
    @Inject(method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"))
    private void hyperspace_wormholes$sink(LivingEntity entity, PoseStack poseStack, float bob, float bodyRot,
                                          float partialTick, float scale, CallbackInfo ci) {
        double offset = NecrozmaSceneCinematic.sink(entity, partialTick);
        if (offset != 0) poseStack.translate(0, offset, 0);
    }
}
