package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.client.battlecamera.BattleCamera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the battlers drawn while the battle camera films them from a spot away from the player's own view: within the
 * entity's own render distance their renderers skip the game's frustum test. Optional (require 0).
 */
@Mixin(EntityRenderer.class)
public abstract class BattleCullingMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void hyperspace_wormholes$battlerNotCulled(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (BattleCamera.battler(entity) && entity.shouldRender(camX, camY, camZ)) cir.setReturnValue(true);
    }
}
