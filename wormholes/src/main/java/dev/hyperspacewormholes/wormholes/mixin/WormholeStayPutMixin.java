package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.WormholeDestinations;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pokemon met through a wormhole never pathfind: a mob tagged WormholeDestinations.STAYS_PUT drops any path it is given
 * and never follows one, so nothing Cobblemon's brain asks of its navigation moves it. It still turns, looks round,
 * battles and falls.
 */
@Mixin(PathNavigation.class)
public abstract class WormholeStayPutMixin {
    @Shadow
    @Final
    protected Mob mob;

    @Shadow
    public abstract void stop();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$stayPut(CallbackInfo ci) {
        if (mob.getTags().contains(WormholeDestinations.STAYS_PUT)) {
            stop();
            ci.cancel();
        }
    }
}
