package dev.hyperspacewormholes.wormholes.ride;

import dev.hyperspacewormholes.wormholes.Wormholes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The Ultra Beast worlds that white wormholes go to, and the way home that stays open while anyone's in one.
 */
public final class UltraBeastWorlds {
    public static final Map<String, ResourceKey<Level>> HOMES = Map.of(
            "nihilego", Wormholes.ULTRA_DEEP_SEA,
            "buzzwole", Wormholes.ULTRA_JUNGLE,
            "xurkitree", Wormholes.ULTRA_PLANT,
            "pheromosa", Wormholes.ULTRA_DESERT,
            "celesteela", Wormholes.ULTRA_VALLEY,
            "guzzlord", Wormholes.ULTRA_RUINS);

    private UltraBeastWorlds() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(UltraBeastWorlds::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals("rctmod")
                    && (isHome(level) || level.dimension() == Wormholes.ULTRA_WARP)) {
                entity.discard();
            }
        });
    }

    public static boolean isHome(Level level) {
        return HOMES.containsValue(level.dimension());
    }

    public static @Nullable ResourceKey<Level> homeOf(@Nullable String properties) {
        if (properties == null) return null;
        for (Map.Entry<String, ResourceKey<Level>> home : HOMES.entrySet()) {
            if (properties.startsWith("species=cobblemon:" + home.getKey() + " ")) return home.getValue();
        }
        return null;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 40 != 0) return;
        for (ResourceKey<Level> key : HOMES.values()) {
            ServerLevel level = server.getLevel(key);
            if (level == null || level.players().isEmpty() || WaysHome.hasOpenUntilEmpty(level)) continue;
            ServerPlayer player = level.players().getFirst();
            if (!player.isAlive() || UltraWarpRide.isRiding(player)) continue;
            HomePoint home = player.getAttached(Wormholes.ULTRA_HOME);
            WormholeDestinations.openWayHomeBeside(player, home != null ? home : HomePoint.worldSpawn(server));
        }
    }
}
