package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cleans up stray duplicate copies of riders' Pokemon that Cobblemon lost track of.
 */
public final class DuplicatePokemonCleanup {
    private static final int CHECK_AFTER_TICKS = 40;

    private record Check(PokemonEntity entity, int at) {}

    private static final List<Check> PENDING = new ArrayList<>();
    private static int ticks;

    private DuplicatePokemonCleanup() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof PokemonEntity pokemon && pokemon.getPokemon().isPlayerOwned()) {
                PENDING.add(new Check(pokemon, ticks + CHECK_AFTER_TICKS));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(DuplicatePokemonCleanup::tick);
    }

    private static void tick(MinecraftServer server) {
        ticks++;
        List<Check> due = new ArrayList<>();
        PENDING.removeIf(c -> {
            if (c.at() > ticks) return false;
            due.add(c);
            return true;
        });
        for (Check c : due) check(server, c.entity());
    }

    private static void check(MinecraftServer server, PokemonEntity entity) {
        if (entity.isRemoved() || entity.isVehicle()) return;
        UUID owner = entity.getPokemon().getOwnerUUID();
        ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
        if (player == null) return;
        UUID id = entity.getPokemon().getUuid();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon == null || !pokemon.getUuid().equals(id)) continue;
            if (pokemon.getEntity() != entity) entity.discard();
            return;
        }
    }
}
