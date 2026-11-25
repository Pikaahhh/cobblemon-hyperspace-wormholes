package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

/**
 * Experience from a player's own Pokemon only joins their own (OwnEncounters; an orb's scope is its owner's while it ticks).
 */
@Mixin(ExperienceOrb.class)
public abstract class OwnOrbMixin {
    @Inject(method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;II)Z", at = @At("HEAD"), cancellable = true)
    private static void hyperspace_wormholes$sameOwner(ExperienceOrb orb, int id, int value, CallbackInfoReturnable<Boolean> cir) {
        if (!Objects.equals(OwnEncounters.owner(orb), OwnEncounters.scope())) cir.setReturnValue(false);
    }
}
