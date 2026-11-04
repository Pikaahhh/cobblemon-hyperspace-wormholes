package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.api.entity.Despawner;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.world.CellFeature;
import dev.hyperspacewormholes.wormholes.world.UltraPlantFeature;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Ultra Plant's Xurkitrees, real Pokemon standing in the statue pose (the aspect STATUE, which Cobblemon's resolver maps
 * to the poser cobblemon:xurkitree_statue). Scenery in two layers, one per chance square with its spot, size and turn from
 * the world seed: SMALL (heights up to 35, in 60% of squares) and GIANT (as tall as the towers), no two closer than their
 * legs' reach (FOOTPRINT of their height) plus GAP. Every EVERY ticks the squares within REACH of each player get theirs
 * once loaded (no AI, unhurtable, unbattleable, tagged SCENERY).
 * <p>
 * The zone's own Xurkitree (WormholeDestinations.spawnWild) stands posed until a non-spectator comes within WAKE: show
 * lightning strikes it and BOLT_TICKS later it drops the pose and cries. Statues never despawn or turn (Cobblemon turns a
 * body to its spawn direction for a client's first second, so that is set too) and are tracked to the whole view distance
 * (StatueTrackingMixin).
 */
public final class PlantXurkitrees {
    public static final String STATUE = "hyperspace_statue";
    private static final String SCENERY = "hyperspace_wormholes.plant_scenery";
    private static final String OLD_SCENERY = "hyperspace_wormholes.plant_statue";
    private static final String WAKE_TAG = "hyperspace_wormholes.plant_wake";
    private static final String HOLD_TAG = "hyperspace_wormholes.plant_hold";
    private static final int EVERY = 40, REACH = 192, CACHE = 50_000, GAP = 3;
    private static final float STATUE_HEIGHT = 5.96f * 0.9f, FOOTPRINT = 0.44f;
    private static final double WAKE = 7;
    private static final int BOLT_TICKS = 3;

    private record Size(int height, int weight) {}

    private static final List<Size> SIZES = List.of(new Size(13, 30), new Size(19, 30), new Size(21, 15),
            new Size(31, 10), new Size(35, 10));
    private static final List<Size> GIANTS = List.of(new Size(45, 3), new Size(55, 3), new Size(64, 2), new Size(72, 2));

    private record Layer(int cell, int chance, int inset, List<Size> sizes, long salt, int rank, Map<Long, Optional<Spot>> spots) {
        int total() {
            return sizes.stream().mapToInt(Size::weight).sum();
        }
    }

    private static final Layer SMALL = new Layer(56, 60, 8, SIZES, 0x5EA1A27L, 0, new HashMap<>());
    private static final Layer GIANT = new Layer(144, 70, 24, GIANTS, 0x61A27E5L, 1, new HashMap<>());
    private static final double GIANT_REACH = 72 * FOOTPRINT + 1;

    private record Spot(int x, int z, int height, float yaw, int rank, long key) {
        double reach() {
            return height * FOOTPRINT + 1;
        }

        boolean beats(Spot other) {
            if (rank != other.rank) return rank > other.rank;
            if (height != other.height) return height > other.height;
            return key < other.key;
        }

        boolean crowds(Spot other) {
            return other != this && Math.hypot(x - other.x, z - other.z) < reach() + other.reach() + GAP;
        }
    }

    private static final Map<PokemonEntity, Integer> WAITING = new HashMap<>();
    private static final Map<PokemonEntity, Float> HELD = new HashMap<>();
    private static final Despawner<PokemonEntity> NEVER = new Despawner<>() {
        @Override
        public void beginTracking(PokemonEntity entity) {}

        @Override
        public boolean shouldDespawn(PokemonEntity entity) {
            return false;
        }
    };

    private PlantXurkitrees() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register(PlantXurkitrees::onLoad);
        ServerTickEvents.END_SERVER_TICK.register(PlantXurkitrees::tick);
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> HELD.remove(entity));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SMALL.spots().clear();
            GIANT.spots().clear();
            WAITING.clear();
            HELD.clear();
        });
    }

    static void sleep(PokemonEntity entity) {
        pose(entity, true);
        entity.addTag(WAKE_TAG);
        entity.addTag(HOLD_TAG);
    }

    static @Nullable Vec3 statueStar(ServerLevel level, Vec3 around, double range, RandomSource random) {
        List<PokemonEntity> statues = level.getEntitiesOfClass(PokemonEntity.class, new AABB(around, around).inflate(range),
                e -> e.getTags().contains(SCENERY));
        if (statues.isEmpty()) return null;
        PokemonEntity statue = statues.get(random.nextInt(statues.size()));
        return statue.position().add(0, statue.getBbHeight() * 0.86, 0);
    }

    public static int trackingRange(Entity entity) {
        if (!entity.getTags().contains(SCENERY) || entity.getServer() == null) return 0;
        return entity.getServer().getPlayerList().getViewDistance() * 16;
    }

    private static void pose(PokemonEntity entity, boolean statue) {
        Pokemon pokemon = entity.getPokemon();
        Set<String> forced = new HashSet<>(pokemon.getForcedAspects());
        if (statue) forced.add(STATUE);
        else forced.remove(STATUE);
        pokemon.setForcedAspects(forced);
        pokemon.updateAspects();
        entity.getEntityData().set(PokemonEntity.getASPECTS(), pokemon.getAspects());
    }

    private static void onLoad(Entity entity, ServerLevel level) {
        if (!(entity instanceof PokemonEntity pokemon)) return;
        if (pokemon.getTags().contains(OLD_SCENERY)) {
            pokemon.discard();
            return;
        }
        if (pokemon.getTags().contains(WAKE_TAG)) WAITING.putIfAbsent(pokemon, -1);
        if (pokemon.getTags().contains(SCENERY) || pokemon.getTags().contains(HOLD_TAG)) {
            pokemon.setDespawner(NEVER);
            float yaw = Mth.wrapDegrees(pokemon.getYRot());
            pokemon.getEntityData().set(PokemonEntity.getSPAWN_DIRECTION(), yaw);
            HELD.put(pokemon, yaw);
        }
    }

    private static void hold() {
        for (Iterator<Map.Entry<PokemonEntity, Float>> it = HELD.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<PokemonEntity, Float> e = it.next();
            PokemonEntity pokemon = e.getKey();
            if (pokemon.isRemoved()) {
                it.remove();
                continue;
            }
            float yaw = e.getValue();
            if (pokemon.getYRot() != yaw || pokemon.yBodyRot != yaw || pokemon.getYHeadRot() != yaw || pokemon.getXRot() != 0) {
                pokemon.setYRot(yaw);
                pokemon.setXRot(0);
                pokemon.setYBodyRot(yaw);
                pokemon.setYHeadRot(yaw);
            }
        }
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(Wormholes.ULTRA_PLANT);
        if (level == null) return;
        wake(level);
        hold();
        if (server.getTickCount() % EVERY != 0 || level.players().isEmpty()) return;
        int reach = Math.max(REACH, server.getPlayerList().getViewDistance() * 16);
        CellFeature.Ground ground = new CellFeature.Ground(level.getChunkSource().getGenerator(), level,
                level.getChunkSource().randomState(), level.getSeed());
        for (Layer layer : List.of(SMALL, GIANT)) {
            if (layer.spots().size() > CACHE) layer.spots().clear();
            Set<Long> done = new HashSet<>();
            for (ServerPlayer player : level.players()) {
                int x = player.getBlockX(), z = player.getBlockZ(), cell = layer.cell();
                for (int cx = Math.floorDiv(x - reach, cell), toX = Math.floorDiv(x + reach, cell); cx <= toX; cx++) {
                    for (int cz = Math.floorDiv(z - reach, cell), toZ = Math.floorDiv(z + reach, cell); cz <= toZ; cz++) {
                        if (!done.add(ChunkPos.asLong(cx, cz))) continue;
                        Spot spot = candidate(layer, ground, cx, cz);
                        if (spot != null && kept(spot, ground)) stand(level, spot);
                    }
                }
            }
        }
    }

    private static @Nullable Spot candidate(Layer layer, CellFeature.Ground ground, int cx, int cz) {
        return layer.spots().computeIfAbsent(ChunkPos.asLong(cx, cz), k -> Optional.ofNullable(spot(layer, ground, cx, cz))).orElse(null);
    }

    private static boolean kept(Spot spot, CellFeature.Ground ground) {
        if (crowded(spot, GIANT, ground, spot.reach() + GIANT_REACH + GAP)) return false;
        return spot.rank() == GIANT.rank() || !crowded(spot, SMALL, ground, 2 * spot.reach() + GAP);
    }

    private static boolean crowded(Spot spot, Layer layer, CellFeature.Ground ground, double far) {
        int cell = layer.cell();
        for (int cx = Math.floorDiv((int) (spot.x() - far), cell), toX = Math.floorDiv((int) (spot.x() + far), cell); cx <= toX; cx++) {
            for (int cz = Math.floorDiv((int) (spot.z() - far), cell), toZ = Math.floorDiv((int) (spot.z() + far), cell); cz <= toZ; cz++) {
                Spot other = candidate(layer, ground, cx, cz);
                if (other != null && spot.crowds(other) && other.beats(spot)) return true;
            }
        }
        return false;
    }

    private static void wake(ServerLevel level) {
        for (Iterator<Map.Entry<PokemonEntity, Integer>> it = WAITING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<PokemonEntity, Integer> w = it.next();
            PokemonEntity e = w.getKey();
            if (e.isRemoved() || e.level() != level) {
                it.remove();
                continue;
            }
            if (w.getValue() < 0) {
                Player near = level.getNearestPlayer(e.getX(), e.getY(), e.getZ(), WAKE, EntitySelector.NO_SPECTATORS);
                if (near == null) continue;
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(e.position());
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                w.setValue(0);
            }
            w.setValue(w.getValue() + 1);
            if (w.getValue() < BOLT_TICKS) continue;
            pose(e, false);
            e.removeTag(WAKE_TAG);
            WormholeSounds.cry(e);
            it.remove();
        }
    }

    private static @Nullable Spot spot(Layer layer, CellFeature.Ground ground, int cx, int cz) {
        RandomSource random = RandomSource.create(ground.seed() ^ cx * 0x9E3779B97F4A7C15L ^ cz * 0xC2B2AE3D27D4EB4FL ^ layer.salt());
        if (random.nextInt(100) >= layer.chance()) return null;
        int x = cx * layer.cell() + layer.inset() + random.nextInt(layer.cell() - 2 * layer.inset());
        int z = cz * layer.cell() + layer.inset() + random.nextInt(layer.cell() - 2 * layer.inset());
        int roll = random.nextInt(layer.total());
        Size size = layer.sizes().get(0);
        for (Size s : layer.sizes()) {
            roll -= s.weight();
            if (roll < 0) {
                size = s;
                break;
            }
        }
        int r = Mth.ceil(size.height() * FOOTPRINT) + 2;
        for (int[] d : new int[][]{{0, 0}, {-r, 0}, {r, 0}, {0, -r}, {0, r}}) {
            if (UltraPlantFeature.occupied(ground, x + d[0], z + d[1])) return null;
        }
        return new Spot(x, z, size.height(), Mth.wrapDegrees(random.nextFloat() * 360f), layer.rank(), ChunkPos.asLong(cx, cz));
    }

    private static void stand(ServerLevel level, Spot spot) {
        int x = spot.x(), z = spot.z();
        ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
        if (!level.hasChunk(chunk.x, chunk.z) || !level.areEntitiesLoaded(chunk.toLong())) return;
        AABB around = new AABB(x - 3, level.getMinBuildHeight(), z - 3, x + 4, level.getMaxBuildHeight(), z + 4);
        if (!level.getEntitiesOfClass(PokemonEntity.class, around, e -> e.getTags().contains(SCENERY)).isEmpty()) return;
        try {
            PokemonEntity entity = PokemonProperties.Companion.parse("xurkitree shiny=false").createEntity(level);
            entity.getPokemon().setScaleModifier(spot.height() / STATUE_HEIGHT);
            pose(entity, true);
            entity.refreshDimensions();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            entity.moveTo(x + 0.5, y, z + 0.5, spot.yaw(), 0f);
            entity.setYHeadRot(spot.yaw());
            entity.setYBodyRot(spot.yaw());
            entity.getEntityData().set(PokemonEntity.getSPAWN_DIRECTION(), spot.yaw());
            entity.setNoAi(true);
            entity.setInvulnerable(true);
            entity.setPersistenceRequired();
            entity.getEntityData().set(PokemonEntity.getUNBATTLEABLE(), true);
            entity.getEntityData().set(PokemonEntity.getHIDE_LABEL(), true);
            entity.addTag(SCENERY);
            entity.setDespawner(NEVER);
            level.addFreshEntity(entity);
        } catch (RuntimeException e) {
            Wormholes.LOGGER.error("Couldn't stand a Xurkitree in the Ultra Plant", e);
        }
    }
}
