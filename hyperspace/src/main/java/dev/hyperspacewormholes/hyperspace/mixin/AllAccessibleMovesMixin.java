package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.hyperspace.LevelScaling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

/**
 * Works out which moves a boosted Pokemon can learn from its level before the Hyperspace boost, so relearn and the TM
 * list don't offer moves early.
 */
@Mixin(value = Pokemon.class, remap = false)
public abstract class AllAccessibleMovesMixin {
    @Inject(method = "getAllAccessibleMoves", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$baseMoves(CallbackInfoReturnable<Set<MoveTemplate>> cir) {
        Pokemon pokemon = (Pokemon) (Object) this;
        if (!LevelScaling.boosted(pokemon)) return;
        int base = LevelScaling.baseLevel(pokemon);
        Set<MoveTemplate> moves = new HashSet<>(pokemon.getForm().getMoves().getLevelUpMovesUpTo(base));
        for (BenchedMove benched : pokemon.getBenchedMoves()) moves.add(benched.getMoveTemplate());
        moves.addAll(pokemon.getForm().getMoves().getEvolutionMoves());
        cir.setReturnValue(moves);
    }
}
