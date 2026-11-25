package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pokemon that come out of a wormhole into Ultra Space stay until the area is left: keep marks them persistent so
 * Cobblemon's despawner never takes them, and tags them with TAG, VISIT and the world's visit number (a player's own,
 * OwnEncounters, with that player's number too). A visit ends when no player (spectators count) is left in the world,
 * checked every SWEEP_EVERY ticks, and for a player's own when that player leaves; the number goes up and every tagged
 * wild Pokemon of it that isn't in a battle is removed, as is any in an unloaded chunk when it loads. A disconnect can
 * come in on the network thread, where C2ME refuses to unload entities, so it's looked at on the server thread.
 */
public final class UltraStays {
    private static final String TAG = "hyperspace_wormholes.ultra_space";
    private static final String VISIT = "hyperspace_wormholes.ultra_visit.";
    private static final int SWEEP_EVERY = 100;

    private static final Map<ResourceKey<Level>, Integer> VISITS = new HashMap<>();
    private static final Set<ResourceKey<Level>> OCCUPIED = new HashSet<>();

    private record Stay(ResourceKey<Level> world, UUID player) {}

    private static final Map<Stay, Integer> OWN_VISITS = new HashMap<>();

    private UltraStays() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            ServerLevel origin = player.server.getLevel(event.getFrom());
            if (origin != null) left(origin, player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone event) -> {
            ServerPlayer oldPlayer = (ServerPlayer) event.getOriginal();
            if (oldPlayer.serverLevel() != event.getEntity().level()) left(oldPlayer.serverLevel(), oldPlayer);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            ServerLevel level = player.serverLevel();
            player.server.execute(() -> left(level, player));
        });
        Wormholes.onEntityLoad(UltraStays::onLoad);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> sweep(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            VISITS.clear();
            OWN_VISITS.clear();
            OCCUPIED.clear();
        });
    }

    static void keep(ServerLevel level, PokemonEntity entity) {
        if (!UltraDimensions.isUltra(level)) return;
        entity.setPersistenceRequired();
        entity.addTag(TAG);
        entity.addTag(visit(level, OwnEncounters.owner(entity)));
    }

    private static String visit(ServerLevel level, @Nullable UUID owner) {
        String visit = VISIT + VISITS.getOrDefault(level.dimension(), 0);
        return owner == null ? visit : visit + "." + OWN_VISITS.getOrDefault(new Stay(level.dimension(), owner), 0);
    }

    static void again(ServerLevel level, UUID owner) {
        OWN_VISITS.merge(new Stay(level.dimension(), owner), 1, Integer::sum);
        for (PokemonEntity pokemon : OwnEncounters.loaded()) {
            if (pokemon.level() == level && owner.equals(OwnEncounters.owner(pokemon)) && stale(pokemon)) pokemon.discard();
        }
    }

    private static boolean empty(ServerLevel level, @Nullable ServerPlayer leaving) {
        return level.players().stream().noneMatch(player -> player != leaving);
    }

    private static void left(ServerLevel level, ServerPlayer leaving) {
        if (!UltraDimensions.isUltra(level)) return;
        again(level, leaving.getUUID());
        if (empty(level, leaving)) emptied(level);
    }

    private static void emptied(ServerLevel level) {
        VISITS.merge(level.dimension(), 1, Integer::sum);
        OWN_VISITS.keySet().removeIf(stay -> stay.world() == level.dimension());
        OCCUPIED.remove(level.dimension());
        level.getEntities(EntityTypeTest.forClass(PokemonEntity.class), UltraStays::stale).forEach(Entity::discard);
    }

    private static boolean stale(PokemonEntity pokemon) {
        if (!pokemon.getTags().contains(TAG) || pokemon.getOwnerUUID() != null || pokemon.isBattling()) return false;
        return !pokemon.getTags().contains(visit((ServerLevel) pokemon.level(), OwnEncounters.owner(pokemon)));
    }

    private static void onLoad(Entity entity, ServerLevel level) {
        if (entity instanceof PokemonEntity pokemon && UltraDimensions.isUltra(level) && stale(pokemon)) pokemon.discard();
    }

    private static void sweep(MinecraftServer server) {
        if (server.getTickCount() % SWEEP_EVERY != 0) return;
        for (PokemonEntity pokemon : OwnEncounters.loaded()) {
            if (!(pokemon.level() instanceof ServerLevel level) || !UltraDimensions.isUltra(level)) continue;
            UUID owner = OwnEncounters.owner(pokemon);
            if (owner != null && level.getPlayerByUUID(owner) == null && !pokemon.isBattling()) again(level, owner);
            if (!pokemon.isRemoved() && stale(pokemon)) pokemon.discard();
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (!UltraDimensions.isUltra(level)) continue;
            if (!empty(level, null)) {
                OCCUPIED.add(level.dimension());
            } else if (OCCUPIED.contains(level.dimension())) {
                emptied(level);
            }
        }
    }
}
