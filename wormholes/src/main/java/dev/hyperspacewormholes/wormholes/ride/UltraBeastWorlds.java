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

import java.util.List;
import java.util.Map;

/**
 * The Ultra Beast worlds that white wormholes go to, and the way home that stays open while anyone's in one; Necrozma's
 * is the Ultra Megalopolis, where it is fought (NecrozmaFight). Ultra Beasts without a world of their own live in the
 * Ultra Space Wilds (WILDS_HOMES), waiting as the world's shadow (WildsShadows). Trainers (anything of the rctmod
 * namespace) are removed as they load in any Ultra Space world.
 */
public final class UltraBeastWorlds {
    public static final Map<String, ResourceKey<Level>> HOMES = Map.ofEntries(
            Map.entry("nihilego", Wormholes.ULTRA_DEEP_SEA),
            Map.entry("buzzwole", Wormholes.ULTRA_JUNGLE),
            Map.entry("xurkitree", Wormholes.ULTRA_PLANT),
            Map.entry("pheromosa", Wormholes.ULTRA_DESERT),
            Map.entry("celesteela", Wormholes.ULTRA_CRATER),
            Map.entry("necrozma", Wormholes.ULTRA_MEGALOPOLIS));
    public static final Map<String, ResourceKey<Level>> WILDS_HOMES = Map.of(
            "guzzlord", Wormholes.WILDS_CRAG,
            "stakataka", Wormholes.WILDS_CRAG,
            "kartana", Wormholes.WILDS_CRAG,
            "blacephalon", Wormholes.WILDS_CAVE,
            "poipole", Wormholes.WILDS_WATER,
            "naganadel", Wormholes.WILDS_CLIFF);

    private UltraBeastWorlds() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(UltraBeastWorlds::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (UltraDimensions.isUltra(level)
                    && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals("rctmod")) {
                entity.discard();
            }
        });
    }

    public static boolean isHome(Level level) {
        return HOMES.containsValue(level.dimension());
    }

    public static @Nullable String beastOf(ResourceKey<Level> home) {
        for (Map.Entry<String, ResourceKey<Level>> entry : HOMES.entrySet()) {
            if (entry.getValue() == home) return entry.getKey();
        }
        return null;
    }

    public static @Nullable ResourceKey<Level> homeOf(@Nullable String properties) {
        if (properties == null) return null;
        for (Map<String, ResourceKey<Level>> homes : List.of(HOMES, WILDS_HOMES)) {
            for (Map.Entry<String, ResourceKey<Level>> home : homes.entrySet()) {
                if (properties.startsWith("species=cobblemon:" + home.getKey() + " ")) return home.getValue();
            }
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
