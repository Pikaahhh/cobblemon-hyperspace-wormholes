package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player's own Pokemon is only heard by that player, and nothing apart from it bumps it (OwnEncounters).
 */
@Mixin(Entity.class)
public abstract class OwnEntityMixin {
    @WrapMethod(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V")
    private void hyperspace_wormholes$heard(SoundEvent sound, float volume, float pitch, Operation<Void> original) {
        OwnEncounters.only((Entity) (Object) this, () -> original.call(sound, volume, pitch));
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$noPush(Entity other, CallbackInfo ci) {
        if (OwnEncounters.apart((Entity) (Object) this, other)) ci.cancel();
    }

    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$noCollision(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (OwnEncounters.apart((Entity) (Object) this, other)) cir.setReturnValue(false);
    }
}
