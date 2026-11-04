package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.PlantXurkitrees;
import dev.hyperspacewormholes.wormholes.ride.StaticPokemon;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tracks the Ultra Plant's statue Xurkitrees and the mod's other still Pokemon out to the whole view distance, not Cobblemon's range. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class StatueTrackingMixin {
    @Shadow
    @Final
    Entity entity;

    @Inject(method = "getEffectiveRange", at = @At("RETURN"), cancellable = true)
    private void hyperspace_wormholes$statueRange(CallbackInfoReturnable<Integer> cir) {
        int range = Math.max(PlantXurkitrees.trackingRange(entity), StaticPokemon.trackingRange(entity));
        if (range > cir.getReturnValue()) cir.setReturnValue(range);
    }
}
