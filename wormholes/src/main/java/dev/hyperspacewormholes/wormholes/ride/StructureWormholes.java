package dev.hyperspacewormholes.wormholes.ride;

import com.mojang.datafixers.util.Pair;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.entity.WormholeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Where purple wormholes go: into the sky near a structure. More rings means rarer structures, up to Legendary
 * Monuments shrines at 3 rings.
 */
public final class StructureWormholes {
    private static final int SEARCH_CHUNKS = 100;
    private static final int ATTEMPTS = 4;
    private static final String DUNGEONS_AND_TAVERNS = "nova_structures";
    private static final int SKY_DISTANCE = 40;
    private static final int SKY_ABOVE = 24;
    private static final int SKY_BEHIND = 12;
    private static final int VOID_SKY_Y = 90;

    private static final Set<String> LARGE_DUNGEONS_AND_TAVERNS = Set.of(
            "badlands_miner_outpost", "bunker", "conduit_ruin", "creeping_crypt", "desert_ruins",
            "end_lighthouse", "end_ship", "hamlet", "illager_hideout", "illager_manor", "jungle_ruins", "lone_citadel",
            "nether_keep", "nether_port", "piglin_donjon", "piglin_outstation", "remnant_big_remnant",
            "remnant_big_remnant_2", "remnant_big_remnant_3", "remnant_birch_graveyard", "remnant_bridge_remnant",
            "remnant_graveyard", "remnant_taiga_castle", "ruin_town", "shrine_combat_tier_3", "shrine_combat_tier_4",
            "shrine_combat_tier_5", "stray_fort", "toxic_lair", "trident_trial_monument", "undead_crypt");

    private static final Set<String> MONUMENTS_WITHOUT_LEGENDS = Set.of(
            "distortion_nexus", "distortion_portal", "dyna_tree", "frost_carrot", "hall_of_origin", "outskirt_stand",
            "thalic_pyramid", "thalic_well", "turnback_cave");

    record Target(ResourceKey<Level> dimension, BlockPos pos, ResourceLocation structure) {}

    record SkySpot(Vec3 pos, float yaw, Vec3 wormholeBase) {}

    private record Entry(ResourceKey<Level> dimension, Holder<Structure> structure, ResourceLocation id) {}

    private static @Nullable List<Entry> catalogue;
    private static @Nullable MinecraftServer catalogueFor;

    private StructureWormholes() {}

    static @Nullable Target find(MinecraftServer server, int rings, int x, int z, long seed) {
        List<Entry> all = catalogue(server);
        Random random = new Random(seed);
        for (int tier = Math.max(0, Math.min(3, rings)); tier >= 0; tier--) {
            int t = tier;
            List<Entry> choices = all.stream().filter(e -> tierOf(e.id()) == t).toList();
            for (int attempt = 0; attempt < ATTEMPTS && !choices.isEmpty(); attempt++) {
                Entry entry = choices.get(random.nextInt(choices.size()));
                ServerLevel level = server.getLevel(entry.dimension());
                if (level == null) continue;
                try {
                    Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(
                            level, HolderSet.direct(entry.structure()), new BlockPos(x, 64, z), SEARCH_CHUNKS, false);
                    if (found != null) return new Target(entry.dimension(), found.getFirst(), entry.id());
                } catch (RuntimeException e) {
                    Wormholes.LOGGER.warn("Couldn't look for {}", entry.id(), e);
                }
            }
        }
        return null;
    }

    private static int tierOf(ResourceLocation id) {
        String namespace = id.getNamespace(), path = id.getPath();
        if (path.contains("well")) return -1;
        if (path.contains("village") || (namespace.equals(DUNGEONS_AND_TAVERNS) && path.equals("witch_villa"))) return 0;
        if (namespace.equals("cobblemon")) return 0;
        if (namespace.equals("minecraft")) return 1;
        if (namespace.equals(DUNGEONS_AND_TAVERNS)) {
            if (path.startsWith("tavern")) return 1;
            if (path.equals("end_castle")) return 3;
            return LARGE_DUNGEONS_AND_TAVERNS.contains(path) ? 2 : -1;
        }
        if (namespace.equals("legendarymonuments")) return MONUMENTS_WITHOUT_LEGENDS.contains(path) ? 2 : 3;
        return -1;
    }

    static String displayName(ResourceLocation structure) {
        String path = structure.getPath();
        return Arrays.stream(path.substring(path.lastIndexOf('/') + 1).split("_")).filter(word -> !word.isEmpty())
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static List<Entry> catalogue(MinecraftServer server) {
        if (catalogue != null && catalogueFor == server) return catalogue;
        List<Entry> entries = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            String namespace = level.dimension().location().getNamespace();
            if (namespace.equals(Wormholes.MOD_ID)) continue;
            for (Holder<StructureSet> set : level.getChunkSource().getGeneratorState().possibleStructureSets()) {
                for (StructureSet.StructureSelectionEntry choice : set.value().structures()) {
                    choice.structure().unwrapKey().ifPresent(key ->
                            entries.add(new Entry(level.dimension(), choice.structure(), key.location())));
                }
            }
        }
        catalogue = entries;
        catalogueFor = server;
        return entries;
    }

    static SkySpot skyNear(ServerLevel level, BlockPos structure, RandomSource random) {
        double start = random.nextDouble() * Math.PI * 2;
        boolean ceiling = level.dimensionType().hasCeiling();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = start + attempt * Math.PI / 4;
            double distance = attempt < 8 ? SKY_DISTANCE : SKY_DISTANCE * 0.6;
            double ax = Math.cos(angle), az = Math.sin(angle);
            int rx = structure.getX() + (int) Math.round(ax * distance);
            int rz = structure.getZ() + (int) Math.round(az * distance);
            int wx = rx + (int) Math.round(ax * SKY_BEHIND), wz = rz + (int) Math.round(az * SKY_BEHIND);
            float yaw = (float) (Mth.atan2(ax, -az) * Mth.RAD_TO_DEG);
            for (int[] c : new int[][]{{rx, rz}, {wx, wz}, {structure.getX(), structure.getZ()}}) level.getChunk(c[0] >> 4, c[1] >> 4);
            Integer y;
            if (!ceiling) {
                int top = Integer.MIN_VALUE;
                for (int[] c : new int[][]{{rx, rz}, {wx, wz}, {structure.getX(), structure.getZ()},
                        {(rx + structure.getX()) / 2, (rz + structure.getZ()) / 2}}) {
                    int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, c[0], c[1]);
                    if (h > level.getMinBuildHeight() + 1) top = Math.max(top, h);
                }
                y = top == Integer.MIN_VALUE ? VOID_SKY_Y : Math.min(top + SKY_ABOVE, level.getMaxBuildHeight() - 12);
            } else {
                y = cavePocket(level, rx, rz, wx, wz);
                if (y == null) continue;
            }
            return skySpot(rx, y, rz, wx, wz, yaw);
        }
        int rx = structure.getX() + SKY_DISTANCE, rz = structure.getZ();
        int y = Math.min(level.getMinBuildHeight() + level.getLogicalHeight() + 8, level.getMaxBuildHeight() - 12);
        return skySpot(rx, y, rz, rx + SKY_BEHIND, rz, 90f);
    }

    private static SkySpot skySpot(int rx, int y, int rz, int wx, int wz, float yaw) {
        Vec3 wormholeBase = new Vec3(wx + 0.5, y + WormholeDestinations.EXIT_AIM_HEIGHT - WormholeEntity.SIZE / 2, wz + 0.5);
        return new SkySpot(new Vec3(rx + 0.5, y, rz + 0.5), yaw, wormholeBase);
    }

    private static @Nullable Integer cavePocket(ServerLevel level, int rx, int rz, int wx, int wz) {
        for (int y = WormholeDestinations.caveTop(level); y > level.getMinBuildHeight() + 8; y--) {
            if (openColumn(level, rx, rz, y - 1, y + 4) && openColumn(level, wx, wz, y - 2, y + 5)) return y;
        }
        return null;
    }

    private static boolean openColumn(ServerLevel level, int x, int z, int fromY, int toY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = fromY; y <= toY; y++) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    static WormholeEntity openWayHome(ServerLevel level, SkySpot spot, HomePoint home, ServerPlayer owner) {
        int lifetime = WormholeDestinations.RETURN_WORMHOLE_TICKS + RiderArrival.TICKS;
        WormholeEntity wormhole = Wormholes.WORMHOLE.create(level);
        wormhole.setWormholeType(WormholeType.PURPLE);
        wormhole.setLifetime(lifetime);
        wormhole.setReturnTo(home);
        Vec3 base = spot.wormholeBase();
        wormhole.moveTo(base.x, base.y, base.z, spot.yaw(), 0f);
        level.addFreshEntity(wormhole);
        WaysHome.track(wormhole, lifetime, owner.getUUID());
        WormholeSounds.open(level, base.add(0, WormholeEntity.SIZE / 2, 0), 0.6f, 1.7f);
        return wormhole;
    }
}
