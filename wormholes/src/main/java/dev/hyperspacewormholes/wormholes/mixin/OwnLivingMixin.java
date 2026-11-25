package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Things apart (OwnEncounters) can't hurt, see or put effects on each other.
 */
@Mixin(LivingEntity.class)
public abstract class OwnLivingMixin {
    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$unhurt(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (OwnEncounters.unhurt((LivingEntity) (Object) this, source)) cir.setReturnValue(true);
    }

    @Inject(method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$unseen(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (OwnEncounters.apart((LivingEntity) (Object) this, other)) cir.setReturnValue(false);
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$unaffected(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (OwnEncounters.apart((LivingEntity) (Object) this, source)) cir.setReturnValue(false);
    }
}
