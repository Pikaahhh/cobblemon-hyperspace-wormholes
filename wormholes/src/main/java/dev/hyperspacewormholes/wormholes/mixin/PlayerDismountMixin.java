package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.UltraWarpRules;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops sneaking from dropping you off your mount mid-ride or while coming out of a wormhole.
 */
@Mixin(Player.class)
public abstract class PlayerDismountMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$stayMounted(CallbackInfoReturnable<Boolean> cir) {
        if (UltraWarpRules.keepsMounted((Player) (Object) this)) cir.setReturnValue(false);
    }
}
