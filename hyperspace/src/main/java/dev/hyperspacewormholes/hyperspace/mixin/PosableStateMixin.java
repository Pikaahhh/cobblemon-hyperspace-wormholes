package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.hyperspace.Cries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Plays our cry sound for Pokemon whose model has no cry animation.
 */
@Mixin(value = PosableState.class, remap = false)
public abstract class PosableStateMixin {
    @Inject(method = "addFirstAnimation", at = @At("HEAD"))
    private void hyperspace_wormholes$fallbackCry(Set<String> animations, CallbackInfo ci) {
        if (!animations.contains("cry")) return;
        PosableState state = (PosableState) (Object) this;
        PosableModel model = state.getCurrentModel();
        if (model == null || model.getAnimation(state, "cry", state.getRuntime()) != null) return;
        if (state.getEntity() instanceof PokemonEntity pokemon) Cries.play(pokemon);
    }
}
