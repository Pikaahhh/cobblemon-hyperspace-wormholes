package dev.hyperspacewormholes.wormholes.boss.mixin;

import dev.hyperspacewormholes.wormholes.ride.NecrozmaFight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sends each player's own weakened Necrozma after the Megalopolis fight to that player only (NecrozmaFight.hiddenFrom):
 * the server never starts tracking another player's copy for them, so their client never has it to draw or click.
 */
@Mixin(Entity.class)
public abstract class BossPrizeTrackingMixin {
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$ownPrizeOnly(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (NecrozmaFight.hiddenFrom((Entity) (Object) this, player)) cir.setReturnValue(false);
    }
}
