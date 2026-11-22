package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.api.tms.TMMoveManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.hyperspace.LevelScaling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Reads a boosted Pokemon's pre-evolution level-up moves at its level from before the Hyperspace boost, so TM
 * compatibility doesn't show moves early.
 */
@Mixin(value = TMMoveManager.class, remap = false)
public abstract class TMMoveManagerMixin {
    @Redirect(method = "getLearnableTMsFromPokemon", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/pokemon/Pokemon;getLevel()I"))
    private int hyperspace_wormholes$baseLevel(Pokemon pokemon) {
        return LevelScaling.baseLevel(pokemon);
    }
}
