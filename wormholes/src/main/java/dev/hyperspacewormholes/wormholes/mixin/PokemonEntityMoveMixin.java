package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.ride.UltraWarpRules;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Holds the mount still through Cobblemon's own movement too during the ride, always poses it as flying, and keeps a
 * flute's wild legend facing the way you're going instead of turning round to look at you.
 */
@Mixin(PokemonEntity.class)
public abstract class PokemonEntityMoveMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$pinMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        if (UltraWarpRules.isPinned((PokemonEntity) (Object) this)) ci.cancel();
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$pinTravel(Vec3 input, CallbackInfo ci) {
        if (UltraWarpRules.isPinned((PokemonEntity) (Object) this)) ci.cancel();
    }

    @Inject(method = "getCurrentPoseType", at = @At("HEAD"), cancellable = true, remap = false)
    private void hyperspace_wormholes$alwaysFlying(CallbackInfoReturnable<PoseType> cir) {
        if (UltraWarpRules.isPinned((PokemonEntity) (Object) this)) cir.setReturnValue(PoseType.FLY);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void hyperspace_wormholes$faceRider(CallbackInfo ci) {
        PokemonEntity self = (PokemonEntity) (Object) this;
        if (self.level().isClientSide() || self.getPokemon().isPlayerOwned() || !UltraWarpRules.inWarp(self)
                || !(self.getFirstPassenger() instanceof Player rider)) return;
        self.setYRot(rider.getYRot());
        self.setYBodyRot(rider.getYRot());
        self.setYHeadRot(rider.getYRot());
        self.setXRot(0f);
    }
}
