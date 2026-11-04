package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.wormholes.ride.NecrozmaFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps the Megalopolis Necrozma's health above zero when a battle knocks it out: Cobblemon passes a wild Pokemon's
 * battle health on to its entity, and an entity at zero dies, plays the faint, despawns and drops its loot and
 * experience. The boss stays on 1 HP until it is beaten (the shared HP never goes lower) and NecrozmaFight heals it
 * then; BossFaintMixin skips the faint itself.
 */
@Mixin(Pokemon.class)
public abstract class BossHealthMixin {
    @ModifyVariable(method = "setCurrentHealth", at = @At("HEAD"), argsOnly = true, remap = false)
    private int hyperspace_wormholes$holdUp(int health) {
        return health < 1 && NecrozmaFight.holdsUp((Pokemon) (Object) this) ? 1 : health;
    }
}
