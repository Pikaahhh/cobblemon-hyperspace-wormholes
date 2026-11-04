package dev.hyperspacewormholes.hyperspace;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point for the Hyperspace half: registers the dimension, items, sounds, entities, loot types and commands. */
public class Hyperspace implements ModInitializer {
    public static final String MOD_ID = "hyperspace_wormholes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final ResourceKey<Level> HYPERSPACE = ResourceKey.create(Registries.DIMENSION, id("hyperspace"));

    @Override
    public void onInitialize() {
        OtherPlayersLegendaries.register();
        Zones.register();
        ModItems.register();
        HoopaStars.register();
        HoopaStarsCount.register();
        ModSounds.register();
        LevelScaling.register();
        Trips.register();
        Portals.register();
        HoopaSummon.register();
        HoopaEvent.register();
        Feeding.register();
        Distortions.register();
        MapDistortions.register();
        NoBlockBreaking.register();
        Spawns.register();
        HyperspaceCommand.register();
        StuckCommand.register();
        HyperspaceLegendaries.register();
        FloatingBalls.register();
        QuickBattles.register();
        Tasks.register();
        DonutPowers.register();
        HyperspaceAdvancements.register();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static boolean inHyperspace(ServerPlayer player) {
        return player.level().dimension().equals(HYPERSPACE);
    }
}
