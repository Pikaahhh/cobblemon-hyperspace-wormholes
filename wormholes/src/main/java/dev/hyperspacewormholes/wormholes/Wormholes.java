package dev.hyperspacewormholes.wormholes;

import com.mojang.serialization.Codec;
import dev.hyperspacewormholes.wormholes.entity.WormholeEntity;
import dev.hyperspacewormholes.wormholes.network.RideEndPayload;
import dev.hyperspacewormholes.wormholes.network.RideFollowPayload;
import dev.hyperspacewormholes.wormholes.network.RideMusicPayload;
import dev.hyperspacewormholes.wormholes.network.RideStartPayload;
import dev.hyperspacewormholes.wormholes.network.RideStatePayload;
import dev.hyperspacewormholes.wormholes.network.RiderEmergeDonePayload;
import dev.hyperspacewormholes.wormholes.network.RiderEmergePayload;
import dev.hyperspacewormholes.wormholes.network.RoarPayload;
import dev.hyperspacewormholes.wormholes.network.RoarRequestPayload;
import dev.hyperspacewormholes.wormholes.network.WarpFlashPayload;
import dev.hyperspacewormholes.wormholes.ride.ItemAnalyser;
import dev.hyperspacewormholes.wormholes.ride.UltraDeepSea;
import dev.hyperspacewormholes.wormholes.ride.PokemonArrivals;
import dev.hyperspacewormholes.wormholes.ride.LegendRoar;
import dev.hyperspacewormholes.wormholes.ride.HomePoint;
import dev.hyperspacewormholes.wormholes.ride.WaysHome;
import dev.hyperspacewormholes.wormholes.ride.RiderArrival;
import dev.hyperspacewormholes.wormholes.ride.SkyWormholes;
import dev.hyperspacewormholes.wormholes.ride.DuplicatePokemonCleanup;
import dev.hyperspacewormholes.wormholes.ride.UltraDimensions;
import dev.hyperspacewormholes.wormholes.ride.UltraBeastWorlds;
import dev.hyperspacewormholes.wormholes.ride.UltraPlantLightning;
import dev.hyperspacewormholes.wormholes.ride.UltraWarpRide;
import dev.hyperspacewormholes.wormholes.world.DeadTreeFeature;
import dev.hyperspacewormholes.wormholes.world.SteamVent;
import dev.hyperspacewormholes.wormholes.world.SwoleTreeFeature;
import dev.hyperspacewormholes.wormholes.world.UltraCraterFeature;
import dev.hyperspacewormholes.wormholes.world.UltraCraterSpireFeature;
import dev.hyperspacewormholes.wormholes.world.UltraDesertExtrasFeature;
import dev.hyperspacewormholes.wormholes.world.UltraDesertFeature;
import dev.hyperspacewormholes.wormholes.world.UltraDesertSandFeature;
import dev.hyperspacewormholes.wormholes.world.UltraPlantFeature;
import dev.hyperspacewormholes.wormholes.world.VolcanoFeature;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the wormholes half. Registers the dimensions, world features, entities, attachments, sounds,
 * packets, commands and all the server-side stuff, plus the built-in Necrozma riding pack.
 */
public class Wormholes implements ModInitializer {
    public static final String MOD_ID = "hyperspace_wormholes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final ResourceKey<Level> ULTRA_WARP = dimension("ultra_warp");
    public static final ResourceKey<Level> ULTRA_DEEP_SEA = dimension("ultra_deep_sea");
    public static final ResourceKey<Level> ULTRA_JUNGLE = dimension("ultra_jungle");
    public static final ResourceKey<Level> ULTRA_PLANT = dimension("ultra_plant");
    public static final ResourceKey<Level> ULTRA_DESERT = dimension("ultra_desert");
    public static final ResourceKey<Level> ULTRA_VALLEY = dimension("ultra_valley");
    public static final ResourceKey<Level> ULTRA_RUINS = dimension("ultra_ruins");

    static {
        feature("ultra_plant_monoliths", new UltraPlantFeature());
        feature("ultra_jungle_volcano", new VolcanoFeature());
        feature("swole_tree", new SwoleTreeFeature());
        feature("ultra_desert_formations", new UltraDesertFeature());
        feature("ultra_desert_sands", new UltraDesertSandFeature());
        feature("ultra_desert_extras", new UltraDesertExtrasFeature());
        feature("ultra_craters", new UltraCraterFeature());
        feature("ultra_crater_spires", new UltraCraterSpireFeature());
        feature("dead_tree", new DeadTreeFeature());
    }

    public static final AttachmentType<HomePoint> RETURN_POINT = attachment("return_point", HomePoint.CODEC);
    public static final AttachmentType<HomePoint> ULTRA_HOME = attachment("deep_sea_home", HomePoint.CODEC);
    public static final AttachmentType<Long> NEXT_RUN = attachment("next_run", Codec.LONG);
    public static final AttachmentType<Long> NEXT_FLUTE = attachment("next_flute", Codec.LONG);

    public static final EntityType<WormholeEntity> WORMHOLE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            id("wormhole"),
            EntityType.Builder.<WormholeEntity>of(WormholeEntity::new, MobCategory.MISC)
                    .sized(WormholeEntity.SIZE, WormholeEntity.SIZE)
                    .clientTrackingRange(32)
                    .updateInterval(20)
                    .fireImmune()
                    .build(MOD_ID + ":wormhole"));

    public static final SoundEvent ULTRA_WORMHOLE_MUSIC = Registry.register(BuiltInRegistries.SOUND_EVENT,
            id("music_disc.ultra_wormhole"), SoundEvent.createVariableRangeEvent(id("music_disc.ultra_wormhole")));

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static ResourceKey<Level> dimension(String path) {
        return ResourceKey.create(Registries.DIMENSION, id(path));
    }

    private static void feature(String path, Feature<?> feature) {
        Registry.register(BuiltInRegistries.FEATURE, id(path), feature);
    }

    private static <T> AttachmentType<T> attachment(String path, Codec<T> codec) {
        return AttachmentRegistry.create(id(path), builder -> builder.persistent(codec).copyOnDeath());
    }

    @Override
    public void onInitialize() {
        WormholeConfig.load();
        PokemonTags.register();
        FabricLoader loader = FabricLoader.getInstance();
        loader.getModContainer("hyperspace_wormholes").ifPresent(mod -> ResourceManagerHelper.registerBuiltinResourcePack(
                id("necrozma_riding"), mod, Component.literal("Necrozma Riding"), ResourcePackActivationType.ALWAYS_ENABLED));
        if (loader.isModLoaded("biomesoplenty")) {
            loader.getModContainer("hyperspace_wormholes").ifPresent(mod -> ResourceManagerHelper.registerBuiltinResourcePack(
                    id("ultra_jungle_volcanoes"), mod, Component.literal("Ultra Jungle Volcanoes"), ResourcePackActivationType.ALWAYS_ENABLED));
            BiomeModifications.addFeature(
                    BiomeSelectors.includeByKey(ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("biomesoplenty", "volcano"))),
                    GenerationStep.Decoration.LOCAL_MODIFICATIONS, ResourceKey.create(Registries.PLACED_FEATURE, id("ultra_jungle_volcano")));
        }

        PayloadTypeRegistry.playS2C().register(RideStartPayload.TYPE, RideStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RideEndPayload.TYPE, RideEndPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RideFollowPayload.TYPE, RideFollowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WarpFlashPayload.TYPE, WarpFlashPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RoarPayload.TYPE, RoarPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RideMusicPayload.TYPE, RideMusicPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RiderEmergePayload.TYPE, RiderEmergePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RiderEmergeDonePayload.TYPE, RiderEmergeDonePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RoarRequestPayload.TYPE, RoarRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RideStatePayload.TYPE, RideStatePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RideStatePayload.TYPE,
                (payload, context) -> UltraWarpRide.onState(context.player(), payload));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                WormholeCommand.register(dispatcher));
        ServerPlayNetworking.registerGlobalReceiver(RoarRequestPayload.TYPE,
                (payload, context) -> LegendRoar.request(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(RiderEmergeDonePayload.TYPE,
                (payload, context) -> RiderArrival.onDone(context.player()));

        UltraHide.register();
        ZCrystals.register();
        UltraSpaceTab.register();
        Flutes.register();
        ItemAnalyser.register();
        SteamVent.register();
        DuplicatePokemonCleanup.register();
        UltraDeepSea.register();
        UltraBeastWorlds.register();
        UltraDimensions.register();
        UltraPlantLightning.register();
        UseBlockCallback.EVENT.register(ItemAnalyser::onUse);
        ServerEntityEvents.ENTITY_LOAD.register(ItemAnalyser::onLightning);
        ServerTickEvents.END_SERVER_TICK.register(ItemAnalyser::tick);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> ItemAnalyser.canBreak(level, pos));
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> ItemAnalyser.onBroken(level, pos, state));

        ServerTickEvents.END_SERVER_TICK.register(UltraWarpRide::tick);
        ServerTickEvents.END_SERVER_TICK.register(LegendRoar::tick);
        ServerTickEvents.END_SERVER_TICK.register(WaysHome::tick);
        ServerTickEvents.END_SERVER_TICK.register(PokemonArrivals::tick);
        ServerTickEvents.END_SERVER_TICK.register(SkyWormholes::tick);
        ServerTickEvents.END_SERVER_TICK.register(RiderArrival::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PokemonArrivals.finishAll();
            RiderArrival.finishAll();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UltraWarpRide.onDisconnect(handler.getPlayer());
            WaysHome.onDisconnect(handler.getPlayer());
            RiderArrival.onDisconnect(handler.getPlayer());
        });
    }
}
