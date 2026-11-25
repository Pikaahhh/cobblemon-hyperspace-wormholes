package dev.hyperspacewormholes.wormholes.boss.mixin;

import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a projectile (a thrown Poke Ball above all) pass through another player's own Pokemon: their weakened Necrozma
 * after the Megalopolis fight or their wormhole Pokemon (OwnEncounters). One that hits its own player's becomes theirs
 * alone too.
 */
@Mixin(Projectile.class)
public abstract class BossPrizeHitMixin {
    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$ownPrizeOnly(Entity target, CallbackInfoReturnable<Boolean> cir) {
        Projectile self = (Projectile) (Object) this;
        if (OwnEncounters.hiddenFrom(target, self.getOwner()) || OwnEncounters.apart(self, target)) cir.setReturnValue(false);
    }

    @Inject(method = "onHit", at = @At("HEAD"))
    private void hyperspace_wormholes$join(HitResult hit, CallbackInfo ci) {
        if (hit instanceof EntityHitResult target) OwnEncounters.join((Projectile) (Object) this, target.getEntity());
    }
}
