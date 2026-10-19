package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.UltraWarpRules;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ignores mouse look while riding through Ultra Space.
 */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$lockView(double yaw, double pitch, CallbackInfo ci) {
        if (UltraWarpRules.isViewLocked((Entity) (Object) this)) ci.cancel();
    }
}
