package dev.hyperspacewormholes.wormholes.boss.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.FaintInstruction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import dev.hyperspacewormholes.wormholes.boss.Bosses;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips Cobblemon's faint for a boss Pokemon's battle copy. The shared HP never reaches 0, but a move that faints
 * outright (Destiny Bond, Perish Song) can still knock it out inside one battle; that battle then ends as that
 * trainer's win, while the real boss (the copy's entity) keeps standing for everyone else.
 */
@Mixin(FaintInstruction.class)
public abstract class BossFaintSkipMixin {
    @Inject(method = "invoke", at = @At("HEAD"), cancellable = true, remap = false)
    private void hyperspace_wormholes$bossStands(PokemonBattle battle, CallbackInfo ci) {
        BattlePokemon fainting = ((FaintInstruction) (Object) this).getFaintingPokemon();
        if (fainting != null && Bosses.of(fainting.getEffectedPokemon()) != null) ci.cancel();
    }
}
