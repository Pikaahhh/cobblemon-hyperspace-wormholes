package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player's own Pokemon only battles them, and drops for them (OwnEncounters). */
@Mixin(PokemonEntity.class)
public abstract class OwnPokemonMixin {
    @Inject(method = "canBattle", at = @At("HEAD"), cancellable = true, remap = false)
    private void hyperspace_wormholes$notTheirs(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (OwnEncounters.hiddenFrom((PokemonEntity) (Object) this, player)) cir.setReturnValue(false);
    }

    @WrapMethod(method = "dropAllDeathLoot")
    private void hyperspace_wormholes$onlyTheirs(ServerLevel level, DamageSource source, Operation<Void> original) {
        OwnEncounters.only((PokemonEntity) (Object) this, () -> original.call(level, source));
    }
}
