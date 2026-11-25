package dev.hyperspacewormholes.wormholes;

import com.mojang.serialization.Codec;
import dev.hyperspacewormholes.Packets;
import dev.hyperspacewormholes.wormholes.entity.WormholeEntity;
import dev.hyperspacewormholes.wormholes.lodestone.Lodestones;
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
import dev.hyperspacewormholes.wormholes.ride.WormholePools;
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
import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import dev.hyperspacewormholes.wormholes.ride.PlantXurkitrees;
import dev.hyperspacewormholes.wormholes.ride.CraterGiants;
import dev.hyperspacewormholes.wormholes.ride.RuinGuzzlord;
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
import com.mojang.serialization.MapCodec;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Entry point for the wormholes half: registers the dimensions, world features, entities, attachments, sounds, packets
 * and commands, plus the built-in Necrozma riding pack and a full mipmaps pack (Cobblemon's disc shelf and tag textures
 * scaled up to multiples of 16, which distant blocks need to get all four mipmap levels).
 */
public class Wormholes {
    public static final String MOD_ID = "hyperspace_wormholes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MOD_ID);
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, MOD_ID);
    private static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIER_TYPES =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, MOD_ID);
    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, MOD_ID);
    private static final DeferredRegister<StructurePlacementType<?>> STRUCTURE_PLACEMENTS =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, MOD_ID);
    private static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, MOD_ID);
    private static final DeferredRegister<StructureProcessorType<?>> STRUCTURE_PROCESSORS =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MOD_ID);
    private static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARGUMENT_TYPES =
            DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, MOD_ID);

    public static final ResourceKey<Level> ULTRA_WARP = dimension("ultra_warp");
    public static final ResourceKey<Level> ULTRA_DEEP_SEA = dimension("ultra_deep_sea");
    public static final ResourceKey<Level> ULTRA_JUNGLE = dimension("ultra_jungle");
    public static final ResourceKey<Level> ULTRA_PLANT = dimension("ultra_plant");
    public static final ResourceKey<Level> ULTRA_DESERT = dimension("ultra_desert");
    public static final ResourceKey<Level> ULTRA_CRATER = dimension("ultra_crater");
    public static final ResourceKey<Level> ULTRA_MEGALOPOLIS = dimension("ultra_megalopolis");
    public static final ResourceKey<Level> ULTRA_RUIN = dimension("ultra_ruin");
    public static final ResourceKey<Level> WILDS_CLIFF = dimension("ultra_wilds_cliff");
    public static final ResourceKey<Level> WILDS_CRAG = dimension("ultra_wilds_crag");
    public static final ResourceKey<Level> WILDS_CAVE = dimension("ultra_wilds_cave");
    public static final ResourceKey<Level> WILDS_WATER = dimension("ultra_wilds_water");

    static {
        feature("ultra_plant_towers", UltraPlantFeature::new);
        feature("ultra_plant_canopy", UltraPlantCanopyFeature::new);
        feature("ultra_desert_formations", UltraDesertFeature::new);
        feature("ultra_desert_sands", UltraDesertSandFeature::new);
        feature("ultra_crater_formations", UltraCraterFeature::new);
        feature("luminescent_ice", LuminescentIceFeature::new);
        feature("wilds_290_rocks", () -> new ZoneRocksFeature("wilds_290",
                IntStream.rangeClosed(1, 20).mapToObj(i -> "wilds_290_rock" + i).toList()));
        feature("jungle_286_trees", () -> new ZoneTreesFeature("jungle_286",
                List.of("jungle_286_tree1", "jungle_286_tree2", "jungle_286_tree3")));
        DENSITY_FUNCTION_TYPES.register("zone_clearing", () -> ZoneClearing.CODEC);
        DENSITY_FUNCTION_TYPES.register("zone_ground", () -> ZoneGround.CODEC);
        PLACEMENT_MODIFIER_TYPES.register("off_zone", () -> OffZoneFilter.TYPE);
        STRUCTURE_TYPES.register("zone", () -> ZoneStructure.TYPE);
        STRUCTURE_PLACEMENTS.register("zone_spot", () -> ZoneSpotPlacement.TYPE);
        STRUCTURE_PIECES.register("zone_tile", () -> ZoneTilePiece.TYPE);
        STRUCTURE_PROCESSORS.register("zone_shading", () -> ZoneProcessors.SHADING);
        STRUCTURE_PROCESSORS.register("waterlog_in_water", () -> ZoneProcessors.WATERLOGGING);
        STRUCTURE_PROCESSORS.register("zone_carve", () -> ZoneProcessors.CARVING);
        for (String world : List.of("ultra_jungle", "ultra_deep_sea", "ultra_space_wilds", "ultra_crater", "ultra_desert", "ultra_plant",
                "ultra_megalopolis", "ultra_ruin")) {
            sound("music." + world);
        }
    }

    public static final Supplier<AttachmentType<HomePoint>> RETURN_POINT = attachment("return_point", HomePoint.CODEC);
    public static final Supplier<AttachmentType<HomePoint>> ULTRA_HOME = attachment("deep_sea_home", HomePoint.CODEC);
    public static final Supplier<AttachmentType<Long>> NEXT_RUN = attachment("next_run", Codec.LONG);
    public static final Supplier<AttachmentType<Integer>> WHITE_WITHOUT_NECROZMA = attachment("white_without_necrozma", Codec.INT);
    /** Whether this player has beaten the bosses: they fight in hard mode from then on and pity is off. */
    public static final Supplier<AttachmentType<Boolean>> NECROZMA_BEATEN = attachment("necrozma_beaten", Codec.BOOL);
    public static final Supplier<AttachmentType<Boolean>> HOOPA_BEATEN = attachment("hoopa_beaten", Codec.BOOL);
    /** Distortion Splicer entries since this player last met Hoopa Unbound (pity at 12, like Necrozma's). */
    public static final Supplier<AttachmentType<Integer>> SPLICES_WITHOUT_HOOPA = attachment("splices_without_hoopa", Codec.INT);
    /** Whether this player has picked up the Hoopa event's Prison Bottle (one copy ever). */
    public static final Supplier<AttachmentType<Boolean>> PRISON_BOTTLE_TAKEN = attachment("prison_bottle_taken", Codec.BOOL);

    /** Whose own an encounter is; nobody else can see or touch it (OwnEncounters). */
    public static final Supplier<AttachmentType<UUID>> OWN_PLAYER = attachment("own_player", UUIDUtil.CODEC);

    public static final DeferredHolder<EntityType<?>, EntityType<WormholeEntity>> WORMHOLE = ENTITY_TYPES.register("wormhole",
            () -> EntityType.Builder.<WormholeEntity>of(WormholeEntity::new, MobCategory.MISC)
                    .sized(WormholeEntity.SIZE, WormholeEntity.SIZE)
                    .clientTrackingRange(32)
                    .updateInterval(20)
                    .fireImmune()
                    .build(MOD_ID + ":wormhole"));

    public static final SoundEvent ULTRA_WORMHOLE_MUSIC = sound("music_disc.ultra_wormhole");

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static ResourceKey<Level> dimension(String path) {
        return ResourceKey.create(Registries.DIMENSION, id(path));
    }

    private static void feature(String path, Supplier<Feature<?>> feature) {
        FEATURES.register(path, feature);
    }

    private static SoundEvent sound(String path) {
        SoundEvent sound = SoundEvent.createVariableRangeEvent(id(path));
        SOUND_EVENTS.register(path, () -> sound);
        return sound;
    }

    private static <T> Supplier<AttachmentType<T>> attachment(String path, Codec<T> codec) {
        return ATTACHMENT_TYPES.register(path, () -> AttachmentType.<T>builder(() -> null).serialize(codec).copyOnDeath().build());
    }

    /** Runs when an entity is added to a server level. One the handler discards never makes it in. */
    public static void onEntityLoad(BiConsumer<Entity, ServerLevel> handler) {
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            handler.accept(event.getEntity(), level);
            if (event.getEntity().isRemoved()) event.setCanceled(true);
        });
    }

    public static void init(IEventBus modBus, ModContainer container) {
        FEATURES.register(modBus);
        DENSITY_FUNCTION_TYPES.register(modBus);
        PLACEMENT_MODIFIER_TYPES.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PLACEMENTS.register(modBus);
        STRUCTURE_PIECES.register(modBus);
        STRUCTURE_PROCESSORS.register(modBus);
        SOUND_EVENTS.register(modBus);
        ENTITY_TYPES.register(modBus);
        ATTACHMENT_TYPES.register(modBus);
        ARGUMENT_TYPES.register(modBus);

        WormholeConfig.load();
        GameplayConfig.register();
        PokemonTags.register();
        WormholePools.register();
        modBus.addListener((AddPackFindersEvent event) -> event.addPackFinders(id("resourcepacks/full_mipmaps"),
                PackType.CLIENT_RESOURCES, Component.literal("Full Mipmaps"), PackSource.BUILT_IN, true, Pack.Position.TOP));
        DesertBlocks.register(modBus);

        Packets.toClient(RideStartPayload.TYPE, RideStartPayload.CODEC);
        Packets.toClient(RideEndPayload.TYPE, RideEndPayload.CODEC);
        Packets.toClient(RideFollowPayload.TYPE, RideFollowPayload.CODEC);
        Packets.toClient(WarpFlashPayload.TYPE, WarpFlashPayload.CODEC);
        Packets.toClient(RoarPayload.TYPE, RoarPayload.CODEC);
        Packets.toClient(RideMusicPayload.TYPE, RideMusicPayload.CODEC);
        Packets.toClient(RiderEmergePayload.TYPE, RiderEmergePayload.CODEC);
        Packets.toServer(RiderEmergeDonePayload.TYPE, RiderEmergeDonePayload.CODEC, (payload, player) -> RiderArrival.onDone(player));
        Packets.toServer(RoarRequestPayload.TYPE, RoarRequestPayload.CODEC, (payload, player) -> LegendRoar.request(player, payload.waystones()));
        Packets.toServer(RideStatePayload.TYPE, RideStatePayload.CODEC, (payload, player) -> UltraWarpRide.onState(player, payload));
        Packets.toClient(UltraBurstPayload.TYPE, UltraBurstPayload.CODEC);
        Packets.toClient(BossTransitionPayload.TYPE, BossTransitionPayload.CODEC);
        Packets.toClient(BossHudPayload.TYPE, BossHudPayload.CODEC);
        Packets.toClient(NecrozmaScenePayload.TYPE, NecrozmaScenePayload.CODEC);
        Packets.toClient(BurnsSkyPayload.TYPE, BurnsSkyPayload.CODEC);
        Packets.toClient(BurnsSkyStopPayload.TYPE, BurnsSkyStopPayload.CODEC);

        ARGUMENT_TYPES.register("wormhole_order", () -> ArgumentTypeInfos.registerByClass(WormholeOrder.Argument.class,
                SingletonArgumentInfo.contextFree(WormholeOrder.Argument::new)));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> WormholeCommand.register(event.getDispatcher()));

        UltraBeastSpawns.register();
        LuminescentIce.register(modBus);
        MegaloLights.register(modBus);
        NaturalShapes.register(modBus);
        UltraSpaceTab.register(modBus);
        ItemAnalyser.register(modBus);
        SteamVent.register(modBus);
        DuplicatePokemonCleanup.register();
        UltraDeepSea.register();
        UltraBeastWorlds.register();
        UltraDimensions.register();
        UltraNoBuilding.register();
        UltraZones.register();
        MegaloKeepOut.register();
        OwnEncounters.register();
        WildsShadows.register();
        ZoneLeaps.register();
        CraterGiants.register();
        RuinGuzzlord.register();
        PlantXurkitrees.register();
        NecrozmaFight.register();
        Bosses.register();
        LightThatBurnsTheSky.register();
        UltraStays.register();
        UltraPlantLightning.register();
        WormholesAdvancements.register(modBus);
        Lodestones.register(modBus);
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            InteractionResult result = ItemAnalyser.onUse(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
            if (result == InteractionResult.PASS) return;
            event.setCanceled(true);
            event.setCancellationResult(result);
        });
        onEntityLoad(ItemAnalyser::onLightning);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> ItemAnalyser.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (!ItemAnalyser.canBreak((Level) event.getLevel(), event.getPos())) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (BlockEvent.BreakEvent event) ->
                ItemAnalyser.onBroken((Level) event.getLevel(), event.getPos(), event.getState()));

        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> UltraWarpRide.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> LegendRoar.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> WaysHome.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> PokemonArrivals.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> RiderArrival.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
            PokemonArrivals.finishAll();
            RiderArrival.finishAll();
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            UltraWarpRide.onDisconnect(player);
            WaysHome.onDisconnect(player);
            RiderArrival.onDisconnect(player);
        });
    }
}
