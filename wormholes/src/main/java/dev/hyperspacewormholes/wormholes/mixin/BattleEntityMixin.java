package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.client.battlecamera.BattleCamera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Leaves out the entities the battle camera wants out of its shot (BattleCamera.hidden: anything but the battlers
 * within 3.5 blocks of the camera or standing between it and the battlers or what it looks at, the player included),
 * by counting them as not to be drawn when the level renderer asks. Optional (require 0).
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class BattleEntityMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void hyperspace_wormholes$battleHidden(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (BattleCamera.hidden(entity)) cir.setReturnValue(false);
    }
}
