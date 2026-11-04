package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;

/**
 * Puts away your sent-out Pokemon (not the one you're riding) before Hyperspace teleports you to another dimension. If
 * Cobblemon removes them mid-teleport it can crash the server (seen with the Hoopa portal), so they are recalled first.
 */
final class Recall {
    static void sentOut(ServerPlayer player) {
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            PokemonEntity entity = pokemon.getEntity();
            if (entity != null && !entity.isRemoved() && entity != player.getVehicle()) pokemon.recall();
        }
    }
}
