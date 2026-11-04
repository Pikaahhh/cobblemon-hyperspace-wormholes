package dev.hyperspacewormholes.wormholes;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import kotlin.Unit;

/** Keeps Ultra Beasts out of natural spawns; they only come through wormholes and the Item Analyser. */
public final class UltraBeastSpawns {
    private UltraBeastSpawns() {}

    public static void register() {
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.HIGHEST, event -> {
            if (event.getEntity().getPokemon().getSpecies().getLabels().contains("ultra_beast")) event.cancel();
            return Unit.INSTANCE;
        });
    }
}
