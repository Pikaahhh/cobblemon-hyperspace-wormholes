package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.FaintInstruction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import dev.hyperspacewormholes.wormholes.ride.NecrozmaFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips Cobblemon's faint for the Megalopolis Necrozma: no fainted line, no zero health and no BATTLE_FAINTED, so nothing that rewards a fainted wild Pokemon fires (Mega Stones). Showdown still ends the battle as a win; the boss stays standing (BossHealthMixin) and NecrozmaFight moves on to the next phase. */
@Mixin(FaintInstruction.class)
public abstract class BossFaintMixin {
    @Inject(method = "invoke", at = @At("HEAD"), cancellable = true, remap = false)
    private void hyperspace_wormholes$standing(PokemonBattle battle, CallbackInfo ci) {
        BattlePokemon fainting = ((FaintInstruction) (Object) this).getFaintingPokemon();
        if (fainting != null && NecrozmaFight.holdsUp(fainting.getEffectedPokemon())) ci.cancel();
    }
}
