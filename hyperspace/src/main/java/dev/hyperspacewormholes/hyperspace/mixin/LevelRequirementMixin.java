package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.requirements.LevelRequirement;
import dev.hyperspacewormholes.hyperspace.LevelScaling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Level-based evolutions check a boosted Pokemon's level from before its Hyperspace boost, so a boost never brings an
 * evolution early.
 */
@Mixin(value = LevelRequirement.class, remap = false)
public abstract class LevelRequirementMixin {
    @Inject(method = "check", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$baseLevel(Pokemon pokemon, CallbackInfoReturnable<Boolean> cir) {
        if (!LevelScaling.boosted(pokemon)) return;
        LevelRequirement self = (LevelRequirement) (Object) this;
        int level = LevelScaling.baseLevel(pokemon);
        cir.setReturnValue(level >= self.getMinLevel() && level <= self.getMaxLevel());
    }
}
