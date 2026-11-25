package dev.hyperspacewormholes.hyperspace;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point for the Hyperspace half: registers the dimension, items, sounds, entities, loot types and commands. */
public class Hyperspace {
    public static final String MOD_ID = "hyperspace_wormholes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final ResourceKey<Level> HYPERSPACE = ResourceKey.create(Registries.DIMENSION, id("hyperspace"));

    public static void init(IEventBus modBus) {
        OtherPlayersLegendaries.register();
        Zones.register();
        ModItems.register(modBus);
        HoopaStars.register(modBus);
        HoopaStarsCount.register(modBus);
        ModSounds.register(modBus);
        LevelScaling.register();
        Trips.register();
        Portals.register();
        HoopaSummon.register();
        HoopaEvent.register();
        Feeding.register();
        Distortions.register(modBus);
        MapDistortions.register();
        NoBlockBreaking.register();
        Spawns.register();
        HyperspaceCommand.register();
        StuckCommand.register();
        HyperspaceLegendaries.register();
        FloatingBalls.register(modBus);
        QuickBattles.register();
        Tasks.register();
        DonutPowers.register();
        HyperspaceAdvancements.register(modBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static boolean inHyperspace(ServerPlayer player) {
        return player.level().dimension().equals(HYPERSPACE);
    }
}
