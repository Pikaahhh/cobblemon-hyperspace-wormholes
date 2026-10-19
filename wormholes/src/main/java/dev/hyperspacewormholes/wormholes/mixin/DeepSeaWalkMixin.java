package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.UltraWarpRules;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes you move like you're on land in the Ultra Deep Sea, walking and running instead of swimming.
 */
@Mixin(Player.class)
public abstract class DeepSeaWalkMixin {
    @Inject(method = "isAffectedByFluids", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$walkTheSeaFloor(CallbackInfoReturnable<Boolean> cir) {
        if (UltraWarpRules.isUnderwater((Player) (Object) this)) cir.setReturnValue(false);
    }
}
