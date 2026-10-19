package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.entity.pokemon.ai.sensors.AlphaLevelMatchingSensor;
import dev.hyperspacewormholes.hyperspace.Hyperspace;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops Cobblemon matching alpha levels to the nearest player in Hyperspace, so they keep the trip's levels.
 */
@Mixin(value = AlphaLevelMatchingSensor.Companion.class, remap = false)
public abstract class AlphaLevelMatchingMixin {
    @Inject(method = "applyLevelMatching", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$keepHyperspaceLevel(PokemonEntity entity, ServerPlayer player, CallbackInfo ci) {
        if (entity.level().dimension().equals(Hyperspace.HYPERSPACE)) ci.cancel();
    }
}
