package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.WormholeDestinations;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pokemon met through a wormhole take no knockback in Ultra Space, so they can't be pushed out of reach;
 * PokemonEntityMoveMixin keeps anything else from moving them sideways.
 */
@Mixin(LivingEntity.class)
public abstract class WormholeKnockbackMixin {
    @Inject(method = "knockback(DDD)V", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$noKnockback(double strength, double x, double z, CallbackInfo ci) {
        if (WormholeDestinations.held((LivingEntity) (Object) this)) ci.cancel();
    }
}
