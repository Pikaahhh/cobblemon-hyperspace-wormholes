package dev.hyperspacewormholes.wormholes.boss.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.wormholes.boss.Bosses;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives a boss Pokemon and its battle copies the shared pool's max HP instead of their own (so their current HP can
 * hold the pool, and Cobblemon's HP bars and capture odds read it), and keeps their HP at 1 or more: a copy's entity is
 * the real boss, and a Pokemon set to 0 HP kills its entity.
 */
@Mixin(Pokemon.class)
public abstract class BossMaxHealthMixin {
    @Inject(method = "getMaxHealth", at = @At("HEAD"), cancellable = true, remap = false)
    private void hyperspace_wormholes$poolMax(CallbackInfoReturnable<Integer> cir) {
        Integer max = Bosses.maxHealth((Pokemon) (Object) this);
        if (max != null) cir.setReturnValue(max);
    }

    @ModifyVariable(method = "setCurrentHealth", at = @At("HEAD"), argsOnly = true, remap = false)
    private int hyperspace_wormholes$poolFloor(int health) {
        return health < 1 && Bosses.of((Pokemon) (Object) this) != null ? 1 : health;
    }
}
