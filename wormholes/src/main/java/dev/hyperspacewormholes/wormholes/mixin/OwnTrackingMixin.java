package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps OwnEncounters' list of what is loaded. An entity's join event is no use for it: it comes before the entity is in
 * the world, and not at all when a chunk's entities come back from being hidden.
 */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class OwnTrackingMixin {
    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void hyperspace_wormholes$loaded(Entity entity, CallbackInfo ci) {
        OwnEncounters.onLoad(entity);
    }

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void hyperspace_wormholes$unloaded(Entity entity, CallbackInfo ci) {
        OwnEncounters.onUnload(entity);
    }
}
