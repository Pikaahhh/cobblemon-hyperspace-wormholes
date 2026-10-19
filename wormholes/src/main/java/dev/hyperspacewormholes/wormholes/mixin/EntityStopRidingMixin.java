package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.UltraWarpRide;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps you on your legend for the whole ride, no matter what tries to knock you off.
 */
@Mixin(Entity.class)
public abstract class EntityStopRidingMixin {
    @Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$stayOnMount(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && UltraWarpRide.blocksDismount(player)) ci.cancel();
    }
}
