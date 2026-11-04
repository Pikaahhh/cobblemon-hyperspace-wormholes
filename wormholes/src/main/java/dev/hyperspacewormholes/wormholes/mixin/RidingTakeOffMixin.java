package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.api.riding.RidingStyle;
import com.cobblemon.mod.common.api.riding.behaviour.ActiveRidingContext;
import com.cobblemon.mod.common.api.riding.behaviour.RidingController;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.ride.Mounts;
import dev.hyperspacewormholes.wormholes.ride.UltraWarpRules;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A legend you first get on in mid-air starts flying, instead of Cobblemon treating it as swimming and throwing you off. */
@Mixin(value = RidingController.class, remap = false)
public abstract class RidingTakeOffMixin {
    @Shadow @Final private PokemonEntity entity;
    @Shadow private ActiveRidingContext context;

    @Inject(method = "checkForNewTransition", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$startFlyingInMidAir(LivingEntity driver, CallbackInfoReturnable<RidingStyle> cir) {
        if (context != null || driver == null || UltraWarpRules.isPinned(entity)) return;
        if (entity.onGround() || entity.isInWater() || entity.isInLava()) return;
        if (Mounts.isAllowed(entity.getPokemon()) && Mounts.canFly(entity.getPokemon())) cir.setReturnValue(RidingStyle.AIR);
    }
}
