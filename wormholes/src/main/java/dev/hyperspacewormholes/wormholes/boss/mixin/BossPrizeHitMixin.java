package dev.hyperspacewormholes.wormholes.boss.mixin;

import dev.hyperspacewormholes.wormholes.ride.NecrozmaFight;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a projectile (a thrown Poke Ball above all) pass through the weakened Necrozma of another player after the
 * Megalopolis fight (NecrozmaFight.hiddenFrom), so every copy standing on the same spot can only be hit, and caught,
 * by its own player's throws.
 */
@Mixin(Projectile.class)
public abstract class BossPrizeHitMixin {
    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$ownPrizeOnly(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (NecrozmaFight.hiddenFrom(target, ((Projectile) (Object) this).getOwner())) cir.setReturnValue(false);
    }
}
