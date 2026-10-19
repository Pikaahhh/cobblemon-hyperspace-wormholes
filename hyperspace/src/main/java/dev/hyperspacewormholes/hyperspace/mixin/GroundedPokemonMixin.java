package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps Pokemon that can't fly on the ground. Cobblemon would flip any Pokemon to flying when its path hit open air
 * (like walking back up a ledge after a contact move), which turned off gravity and had it sliding through the air.
 * Ridden ones are left alone.
 */
@Mixin(value = PokemonEntity.class, remap = false)
public abstract class GroundedPokemonMixin {
    @Inject(method = "setFlying", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$onlyFlyersFly(boolean state, CallbackInfo ci) {
        PokemonEntity pokemon = (PokemonEntity) (Object) this;
        if (state && !pokemon.canFly() && !pokemon.isVehicle()) ci.cancel();
    }
}
