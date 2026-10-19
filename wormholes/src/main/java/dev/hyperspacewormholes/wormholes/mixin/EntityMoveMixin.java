package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.UltraWarpRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the rider and mount moving while they're held in place in Ultra Space.
 */
@Mixin(Entity.class)
public abstract class EntityMoveMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$pinInTunnel(MoverType type, Vec3 movement, CallbackInfo ci) {
        if (UltraWarpRules.isPinned((Entity) (Object) this)) ci.cancel();
    }
}
