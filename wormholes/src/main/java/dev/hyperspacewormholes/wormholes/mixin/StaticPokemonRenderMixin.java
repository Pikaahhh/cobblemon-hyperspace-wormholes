package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.ride.StaticPokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Draws the mod's still Pokemon (StaticPokemon) at any distance the client has them, instead of only within a few times their size scaled by the entity distance setting; the server only sends them within its view distance. */
@Mixin(PokemonEntity.class)
public abstract class StaticPokemonRenderMixin {
    @Inject(method = "shouldRenderAtSqrDistance", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$always(double distance, CallbackInfoReturnable<Boolean> cir) {
        if (StaticPokemon.is((PokemonEntity) (Object) this)) cir.setReturnValue(true);
    }
}
