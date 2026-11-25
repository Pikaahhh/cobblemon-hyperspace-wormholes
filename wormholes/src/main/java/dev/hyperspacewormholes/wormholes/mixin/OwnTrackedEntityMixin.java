package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

/**
 * Takes something back from the clients it no longer exists for (OwnEncounters.give: a ball that hits a player's own
 * Pokemon).
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class OwnTrackedEntityMixin implements OwnEncounters.Tracked {
    @Shadow
    public abstract void updatePlayers(List<ServerPlayer> players);

    @Override
    public void hyperspace_wormholes$retrack(List<ServerPlayer> players) {
        updatePlayers(players);
    }
}
