package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.UltraWarpRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops you swimming in the Ultra Deep Sea, so sprinting is a run along the floor.
 */
@Mixin(Entity.class)
public abstract class DeepSeaSwimMixin {
    @Inject(method = "updateSwimming", at = @At("TAIL"))
    private void hyperspace_wormholes$noSwimming(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof Player && self.isSwimming() && UltraWarpRules.isUnderwater(self)) self.setSwimming(false);
    }
}
