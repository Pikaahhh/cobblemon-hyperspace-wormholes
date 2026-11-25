package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Opens the scope of a player's own Pokemon round its tick, keeps particles in a scope to its player, and makes what drops
 * in one theirs (OwnEncounters).
 */
@Mixin(ServerLevel.class)
public abstract class OwnServerLevelMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void hyperspace_wormholes$tickStarts(Entity entity, CallbackInfo ci) {
        OwnEncounters.tickStarts(entity);
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void hyperspace_wormholes$tickEnds(Entity entity, CallbackInfo ci) {
        OwnEncounters.tickEnds();
    }

    @Inject(method = "addFreshEntity", at = @At("HEAD"))
    private void hyperspace_wormholes$appears(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        OwnEncounters.appears(entity);
    }

    @Inject(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
            at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$seen(ServerPlayer player, boolean far, double x, double y, double z, Packet<?> packet,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (!OwnEncounters.heard(player)) cir.setReturnValue(false);
    }
}
