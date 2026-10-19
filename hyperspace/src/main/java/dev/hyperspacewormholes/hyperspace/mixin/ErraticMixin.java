package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.api.pokemon.experience.Erratic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives Cobblemon's Erratic EXP group sensible numbers past level 100, since the cap goes up to 200.
 */
@Mixin(value = Erratic.class, remap = false)
public class ErraticMixin {
    @Inject(method = "getExperience", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$pastHundred(int level, CallbackInfoReturnable<Integer> cir) {
        if (level > 100) cir.setReturnValue((int) ((long) level * level * level * 6 / 10));
    }
}
