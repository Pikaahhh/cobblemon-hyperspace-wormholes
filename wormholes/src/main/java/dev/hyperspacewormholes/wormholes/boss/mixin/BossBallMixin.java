package dev.hyperspacewormholes.wormholes.boss.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonServerDelegate;
import dev.hyperspacewormholes.wormholes.boss.Bosses;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A Poke Ball hitting a boss Pokemon belongs to the thrower's own battle against it, not to whichever battle the entity
 * last joined; none while the boss is already in a ball (Cobblemon then says it's busy) or when the thrower isn't
 * fighting it.
 */
@Mixin(EmptyPokeBallEntity.class)
public abstract class BossBallMixin {
    // onHitEntity is Projectile's method, so it goes through the refmap; only the call inside is Cobblemon's
    @Redirect(method = "onHitEntity", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/entity/pokemon/PokemonServerDelegate;getBattle()Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;",
            remap = false))
    private PokemonBattle hyperspace_wormholes$throwersBattle(PokemonServerDelegate delegate) {
        return Bosses.ballBattle(delegate.getEntity(), (EmptyPokeBallEntity) (Object) this, delegate.getBattle());
    }
}
