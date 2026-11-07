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
import dev.hyperspacewormholes.wormholes.network.BurnsSkyPayload;
import dev.hyperspacewormholes.wormholes.network.BurnsSkyStopPayload;
import dev.hyperspacewormholes.wormholes.network.RoarPayload;
import dev.hyperspacewormholes.wormholes.network.RoarRequestPayload;
import dev.hyperspacewormholes.wormholes.network.BossHudPayload;
import dev.hyperspacewormholes.wormholes.network.BossTransitionPayload;
import dev.hyperspacewormholes.wormholes.network.NecrozmaScenePayload;
import dev.hyperspacewormholes.wormholes.network.UltraBurstPayload;
import dev.hyperspacewormholes.wormholes.network.WarpFlashPayload;
import dev.hyperspacewormholes.wormholes.ride.ItemAnalyser;
import dev.hyperspacewormholes.wormholes.ride.NecrozmaFight;
import dev.hyperspacewormholes.wormholes.boss.Bosses;
import dev.hyperspacewormholes.wormholes.ride.UltraDeepSea;
import dev.hyperspacewormholes.wormholes.ride.PokemonArrivals;
import dev.hyperspacewormholes.wormholes.ride.LegendRoar;
import dev.hyperspacewormholes.wormholes.ride.HomePoint;
import dev.hyperspacewormholes.wormholes.ride.WaysHome;
import dev.hyperspacewormholes.wormholes.ride.RiderArrival;
import dev.hyperspacewormholes.wormholes.ride.DuplicatePokemonCleanup;
import dev.hyperspacewormholes.wormholes.ride.UltraDimensions;
import dev.hyperspacewormholes.wormholes.ride.UltraNoBuilding;
import dev.hyperspacewormholes.wormholes.ride.MegaloKeepOut;
import dev.hyperspacewormholes.wormholes.ride.UltraStays;
import dev.hyperspacewormholes.wormholes.ride.PlantXurkitrees;
import dev.hyperspacewormholes.wormholes.ride.CraterGiants;
import dev.hyperspacewormholes.wormholes.ride.ZoneLeaps;
import dev.hyperspacewormholes.wormholes.ride.UltraBeastWorlds;
import dev.hyperspacewormholes.wormholes.ride.UltraPlantLightning;
import dev.hyperspacewormholes.wormholes.ride.UltraWarpRide;
import dev.hyperspacewormholes.wormholes.ride.UltraZones;
import dev.hyperspacewormholes.wormholes.ride.WildsShadows;
import dev.hyperspacewormholes.wormholes.ride.WormholeOrder;
import dev.hyperspacewormholes.wormholes.world.SteamVent;
import dev.hyperspacewormholes.wormholes.world.UltraDesertFeature;
import dev.hyperspacewormholes.wormholes.world.UltraDesertSandFeature;
import dev.hyperspacewormholes.wormholes.world.UltraPlantCanopyFeature;
import dev.hyperspacewormholes.wormholes.world.UltraPlantFeature;
import dev.hyperspacewormholes.wormholes.world.UltraCraterFeature;
import dev.hyperspacewormholes.wormholes.world.LuminescentIceFeature;
import dev.hyperspacewormholes.wormholes.world.OffZoneFilter;
import dev.hyperspacewormholes.wormholes.world.ZoneClearing;
import dev.hyperspacewormholes.wormholes.world.ZoneGround;
import dev.hyperspacewormholes.wormholes.world.ZoneProcessors;
import dev.hyperspacewormholes.wormholes.world.ZoneRocksFeature;
import dev.hyperspacewormholes.wormholes.world.ZoneSpotPlacement;
import dev.hyperspacewormholes.wormholes.world.ZoneStructure;
import dev.hyperspacewormholes.wormholes.world.ZoneTilePiece;
import dev.hyperspacewormholes.wormholes.world.ZoneTreesFeature;
import dev.hyperspacewormholes.wormholes.zmove.LightThatBurnsTheSky;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
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
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
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
import net.minecraft.world.level.levelgen.feature.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Entry point for the wormholes half: registers the dimensions, world features, entities, attachments, sounds, packets
 * and commands, plus the built-in Necrozma riding pack and a full mipmaps pack (Cobblemon's disc shelf and tag textures
 * scaled up to multiples of 16, which distant blocks need to get all four mipmap levels).
 */
public class Wormholes implements ModInitializer {
    public static final String MOD_ID = "hyperspace_wormholes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final ResourceKey<Level> ULTRA_WARP = dimension("ultra_warp");
    public static final ResourceKey<Level> ULTRA_DEEP_SEA = dimension("ultra_deep_sea");
    public static final ResourceKey<Level> ULTRA_JUNGLE = dimension("ultra_jungle");
    public static final ResourceKey<Level> ULTRA_PLANT = dimension("ultra_plant");
    public static final ResourceKey<Level> ULTRA_DESERT = dimension("ultra_desert");
    public static final ResourceKey<Level> ULTRA_CRATER = dimension("ultra_crater");
    public static final ResourceKey<Level> ULTRA_MEGALOPOLIS = dimension("ultra_megalopolis");
    public static final ResourceKey<Level> WILDS_CLIFF = dimension("ultra_wilds_cliff");
    public static final ResourceKey<Level> WILDS_CRAG = dimension("ultra_wilds_crag");
    public static final ResourceKey<Level> WILDS_CAVE = dimension("ultra_wilds_cave");
    public static final ResourceKey<Level> WILDS_WATER = dimension("ultra_wilds_water");

    static {
        feature("ultra_plant_towers", new UltraPlantFeature());
        feature("ultra_plant_canopy", new UltraPlantCanopyFeature());
        feature("ultra_desert_formations", new UltraDesertFeature());
        feature("ultra_desert_sands", new UltraDesertSandFeature());
        feature("ultra_crater_formations", new UltraCraterFeature());
        feature("luminescent_ice", new LuminescentIceFeature());
        feature("wilds_290_rocks", new ZoneRocksFeature("wilds_290",
                IntStream.rangeClosed(1, 20).mapToObj(i -> "wilds_290_rock" + i).toList()));
        feature("jungle_286_trees", new ZoneTreesFeature("jungle_286",
                List.of("jungle_286_tree1", "jungle_286_tree2", "jungle_286_tree3")));
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("zone_clearing"), ZoneClearing.CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("zone_ground"), ZoneGround.CODEC);
        Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, id("off_zone"), OffZoneFilter.TYPE);
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE, id("zone"), ZoneStructure.TYPE);
        Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, id("zone_spot"), ZoneSpotPlacement.TYPE);
        Registry.register(BuiltInRegistries.STRUCTURE_PIECE, id("zone_tile"), ZoneTilePiece.TYPE);
        Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR, id("zone_shading"), ZoneProcessors.SHADING);
        Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR, id("waterlog_in_water"), ZoneProcessors.WATERLOGGING);
        Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR, id("zone_carve"), ZoneProcessors.CARVING);
        for (String world : List.of("ultra_jungle", "ultra_deep_sea", "ultra_space_wilds", "ultra_crater", "ultra_desert", "ultra_plant",
                "ultra_megalopolis")) {
            Registry.register(BuiltInRegistries.SOUND_EVENT, id("music." + world), SoundEvent.createVariableRangeEvent(id("music." + world)));
        }
    }

    public static final AttachmentType<HomePoint> RETURN_POINT = attachment("return_point", HomePoint.CODEC);
    public static final AttachmentType<HomePoint> ULTRA_HOME = attachment("deep_sea_home", HomePoint.CODEC);
    public static final AttachmentType<Long> NEXT_RUN = attachment("next_run", Codec.LONG);
    public static final AttachmentType<Integer> WHITE_WITHOUT_NECROZMA = attachment("white_without_necrozma", Codec.INT);
    /** Whether this player has beaten the bosses: they fight in hard mode from then on and pity is off. */
    public static final AttachmentType<Boolean> NECROZMA_BEATEN = attachment("necrozma_beaten", Codec.BOOL);
    public static final AttachmentType<Boolean> HOOPA_BEATEN = attachment("hoopa_beaten", Codec.BOOL);
    /** Distortion Splicer entries since this player last met Hoopa Unbound (pity at 12, like Necrozma's). */
    public static final AttachmentType<Integer> SPLICES_WITHOUT_HOOPA = attachment("splices_without_hoopa", Codec.INT);
    /** Whether this player has picked up the Hoopa event's Prison Bottle (one copy ever). */
    public static final AttachmentType<Boolean> PRISON_BOTTLE_TAKEN = attachment("prison_bottle_taken", Codec.BOOL);

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
        GameplayConfig.register();
        PokemonTags.register();
        FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(mod -> {
            ResourceManagerHelper.registerBuiltinResourcePack(id("full_mipmaps"), mod, Component.literal("Full Mipmaps"),
                    ResourcePackActivationType.ALWAYS_ENABLED);
        });
        DesertBlocks.register();

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
        PayloadTypeRegistry.playS2C().register(UltraBurstPayload.TYPE, UltraBurstPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BossTransitionPayload.TYPE, BossTransitionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BossHudPayload.TYPE, BossHudPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NecrozmaScenePayload.TYPE, NecrozmaScenePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BurnsSkyPayload.TYPE, BurnsSkyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BurnsSkyStopPayload.TYPE, BurnsSkyStopPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RideStatePayload.TYPE,
                (payload, context) -> UltraWarpRide.onState(context.player(), payload));
        ArgumentTypeRegistry.registerArgumentType(id("wormhole_order"), WormholeOrder.Argument.class,
                SingletonArgumentInfo.contextFree(WormholeOrder.Argument::new));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                WormholeCommand.register(dispatcher));
        ServerPlayNetworking.registerGlobalReceiver(RoarRequestPayload.TYPE,
                (payload, context) -> LegendRoar.request(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(RiderEmergeDonePayload.TYPE,
                (payload, context) -> RiderArrival.onDone(context.player()));

        UltraBeastSpawns.register();
        LuminescentIce.register();
        MegaloLights.register();
        NaturalShapes.register();
        UltraSpaceTab.register();
        ItemAnalyser.register();
        SteamVent.register();
        DuplicatePokemonCleanup.register();
        UltraDeepSea.register();
        UltraBeastWorlds.register();
        UltraDimensions.register();
        UltraNoBuilding.register();
        UltraZones.register();
        MegaloKeepOut.register();
        WildsShadows.register();
        ZoneLeaps.register();
        CraterGiants.register();
        PlantXurkitrees.register();
        NecrozmaFight.register();
        Bosses.register();
        LightThatBurnsTheSky.register();
        UltraStays.register();
        UltraPlantLightning.register();
        WormholesAdvancements.register();
        UseBlockCallback.EVENT.register(ItemAnalyser::onUse);
        ServerEntityEvents.ENTITY_LOAD.register(ItemAnalyser::onLightning);
        ServerTickEvents.END_SERVER_TICK.register(ItemAnalyser::tick);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> ItemAnalyser.canBreak(level, pos));
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> ItemAnalyser.onBroken(level, pos, state));

        ServerTickEvents.END_SERVER_TICK.register(UltraWarpRide::tick);
        ServerTickEvents.END_SERVER_TICK.register(LegendRoar::tick);
        ServerTickEvents.END_SERVER_TICK.register(WaysHome::tick);
        ServerTickEvents.END_SERVER_TICK.register(PokemonArrivals::tick);
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
