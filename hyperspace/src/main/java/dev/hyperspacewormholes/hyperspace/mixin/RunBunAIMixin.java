package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.InBattleGimmickMove;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Lets the RunBun trainer AI use Z-Moves and Dynamax when its move allows it.
 */
@Pseudo
@Mixin(targets = "com.gitlab.surilexa.rbrctai.api.ai.RunBunAI", remap = false)
public class RunBunAIMixin {
    @Inject(method = "choose", at = @At("RETURN"))
    private void hyperspace_wormholes$zMovesAndDynamax(ActiveBattlePokemon active, PokemonBattle battle, BattleSide side,
                                           ShowdownMoveset moveset, boolean forceSwitch,
                                           CallbackInfoReturnable<ShowdownActionResponse> cir) {
        if (!(cir.getReturnValue() instanceof MoveActionResponse move) || move.getGimmickID() != null || moveset == null) return;

        List<InBattleGimmickMove> zMoves = moveset.getCanZMove();
        if (zMoves != null) {
            for (int i = 0; i < moveset.moves.size() && i < zMoves.size(); i++) {
                if (moveset.moves.get(i).getId().equals(move.getMoveName()) && zMoves.get(i) != null) {
                    move.setGimmickID(ShowdownMoveset.Gimmick.Z_POWER.getId());
                    return;
                }
            }
        }

        if (moveset.getCanDynamax() && active.getBattlePokemon() != null
                && active.getBattlePokemon().getEffectedPokemon().getGmaxFactor()) {
            move.setGimmickID(ShowdownMoveset.Gimmick.DYNAMAX.getId());
        }
    }
}
