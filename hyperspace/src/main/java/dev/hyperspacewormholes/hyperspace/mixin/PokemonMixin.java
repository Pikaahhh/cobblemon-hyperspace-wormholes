package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.hyperspace.Spawns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Doubles boss health and lets alpha bosses be scaled up.
 */
@Mixin(value = Pokemon.class, remap = false)
public abstract class PokemonMixin {
    @Inject(method = "getMaxHealth", at = @At("RETURN"), cancellable = true)
    private void hyperspace_wormholes$bossHealth(CallbackInfoReturnable<Integer> cir) {
        Pokemon pokemon = (Pokemon) (Object) this;
        if (Spawns.isBoss(pokemon) || Spawns.megaBoss(pokemon) != null) cir.setReturnValue(cir.getReturnValue() * 2);
    }

    @Inject(method = "getEffectiveScale", at = @At("RETURN"), cancellable = true)
    private void hyperspace_wormholes$alphaBossScale(CallbackInfoReturnable<Float> cir) {
        Pokemon pokemon = (Pokemon) (Object) this;
        if (pokemon.isAlpha() && pokemon.getScaleModifier() != 1f) cir.setReturnValue(cir.getReturnValue() * pokemon.getScaleModifier());
    }
}
