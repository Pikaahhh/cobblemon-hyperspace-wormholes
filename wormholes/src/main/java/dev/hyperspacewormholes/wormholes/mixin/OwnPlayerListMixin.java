package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Keeps the sounds and level events in the scope of a player's own Pokemon to that player (OwnEncounters.scope).
 */
@Mixin(PlayerList.class)
public abstract class OwnPlayerListMixin {
    @Shadow
    public abstract ServerPlayer getPlayer(UUID id);

    @Inject(method = "broadcast", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$heard(Player except, double x, double y, double z, double range, ResourceKey<Level> dimension,
                                            Packet<?> packet, CallbackInfo ci) {
        UUID only = OwnEncounters.scope();
        if (only == null) return;
        ci.cancel();
        ServerPlayer player = getPlayer(only);
        if (player != null && player != except && player.level().dimension() == dimension
                && player.distanceToSqr(x, y, z) < range * range) player.connection.send(packet);
    }
}
