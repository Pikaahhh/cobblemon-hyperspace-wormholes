package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Things apart (OwnEncounters) never look at or go for each other.
 */
@Mixin(TargetingConditions.class)
public abstract class OwnTargetingMixin {
    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$unseen(LivingEntity targeter, LivingEntity target,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (OwnEncounters.apart(targeter, target)) cir.setReturnValue(false);
    }
}
