package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.api.net.NetworkPacket;
import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cobblemon's cries, animations and particles of a player's own Pokemon, which it sends to everyone near, only go to that
 * player (OwnEncounters.withheld).
 */
@Mixin(value = CobblemonNetwork.class, remap = false)
public abstract class OwnPacketsMixin {
    @Inject(method = "sendPacketToPlayer", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$withheld(ServerPlayer player, NetworkPacket<?> packet, CallbackInfo ci) {
        if (OwnEncounters.withheld(player, packet)) ci.cancel();
    }
}
