package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.world.CraterLayout;
import dev.hyperspacewormholes.wormholes.world.Zone;
import kotlin.Unit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Ultra Crater's Celesteela: scenery giants in the big craters (CraterLayout.giants, BIG_PIECES ones only), all holding
 * still, plus the catchable one at the plateau crater's wild spot, which fitCrater scales up to CRATER_SCALE.
 * <p>
 * Every EVERY ticks, per player's zone copy, missing giants are made once their chunk is loaded (unhurtable, unbattleable,
 * tagged TAG, on the crater floor, held in the first pose of their idle: the aspect PlantXurkitrees.STATUE); moving or
 * removing one as it loads waits for the next tick (SETTLE, DROP), since the game is walking that chunk's entities.
 * <p>
 * The catchable one faces the walk's nearest end (walkEnd) and gets a hitbox round its body (CraterGiantHitboxMixin) so a
 * Poke Ball hits it, and a Pokemon sent out while the player looks at it within REACH challenges it (CraterGiantReachMixin).
 * Cobblemon turns a body to its SPAWN_DIRECTION for 20 ticks after it is made or loaded, so that is set to its facing.
 */
public final class CraterGiants {
    private static final String TAG = "hyperspace_wormholes.crater_giant";
    private static final int EVERY = 40;
    private static final float SMALLEST = 5f, LARGEST = 7f;
    private static final float CRATER_SCALE = 4f;
    private static final float BODY_WIDTH = 2.5f, BODY_HEIGHT = 6.5f;
    private static final String KEPT_SCALE = "hyperspace_wormholes:crater_scale";
    public static final String FILLS = "hyperspace_wormholes.crater_celesteela";
    private static final String CRATER_ASPECT = "hyperspace_crater_giant";
    private static final Set<Integer> BIG_PIECES = Set.of(1, 2, 3, 4, 11);
    private static final int WALK_SEARCH = 48;
    private static final double REACH = 40;
    private static final int FLOOR_SEARCH = 48;
    private static final List<PokemonEntity> SETTLE = new ArrayList<>(), DROP = new ArrayList<>();

    private CraterGiants() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CraterGiants::tick);
        ServerEntityEvents.ENTITY_LOAD.register(CraterGiants::onLoad);
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.HIGHEST, event -> {
            shrink(event.getPokemon());
            return Unit.INSTANCE;
        });
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(Priority.NORMAL, event -> {
            for (BattleActor actor : event.getBattle().getActors()) {
                for (BattlePokemon pokemon : actor.getPokemonList()) {
                    PokemonEntity entity = pokemon.getEntity();
                    if (entity != null && entity.getTags().contains(FILLS)) still(entity, false);
                }
            }
            return Unit.INSTANCE;
        });
    }

    public static @Nullable EntityDimensions body(PokemonEntity entity) {
        if (!fills(entity)) return null;
        float scale = entity.getPokemon().getForm().getBaseScale() * entity.getPokemon().getScaleModifier();
        return EntityDimensions.scalable(BODY_WIDTH * scale, BODY_HEIGHT * scale);
    }

    static boolean fillsCrater(ServerLevel level, PokemonEntity entity) {
        return level.dimension() == Wormholes.ULTRA_CRATER
                && entity.getPokemon().getSpecies().getResourceIdentifier().getPath().equals("celesteela");
    }

    static void fitCrater(PokemonEntity entity) {
        Pokemon pokemon = entity.getPokemon();
        pokemon.getPersistentData().putFloat(KEPT_SCALE, pokemon.getScaleModifier());
        pokemon.setScaleModifier(CRATER_SCALE);
        entity.addTag(FILLS);
        Set<String> forced = new HashSet<>(pokemon.getForcedAspects());
        forced.add(CRATER_ASPECT);
        pokemon.setForcedAspects(forced);
        still(entity, true);
        entity.refreshDimensions();
    }

    public static boolean fills(PokemonEntity entity) {
        return showsFilling(entity) || entity.getPokemon().getAspects().contains(CRATER_ASPECT);
    }

    public static boolean showsFilling(PokemonEntity entity) {
        return entity.getEntityData().get(PokemonEntity.getASPECTS()).contains(CRATER_ASPECT);
    }

    public static boolean inReach(Player player, PokemonEntity giant) {
        if (!fills(giant) || giant.getOwner() != null || player.level() != giant.level()) return false;
        EntityDimensions body = body(giant);
        if (body == null) return false;
        Vec3 eye = player.getEyePosition();
        return body.makeBoundingBox(giant.position()).clip(eye, eye.add(player.getLookAngle().scale(REACH))).isPresent();
    }

    public static @Nullable PokemonEntity lookedAt(Player player) {
        AABB around = player.getBoundingBox().inflate(REACH);
        for (PokemonEntity giant : player.level().getEntitiesOfClass(PokemonEntity.class, around, CraterGiants::fills)) {
            if (inReach(player, giant)) return giant;
        }
        return null;
    }

    private static void face(PokemonEntity entity, float yaw) {
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
        entity.getEntityData().set(PokemonEntity.getSPAWN_DIRECTION(), Mth.wrapDegrees(yaw));
    }

    static @Nullable Vec3 walkEnd(Zone zone, Vec3 spot) {
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -WALK_SEARCH; dx <= WALK_SEARCH; dx++) {
            for (int dz = -WALK_SEARCH; dz <= WALK_SEARCH; dz++) {
                double distance = dx * dx + dz * dz;
                if (distance >= bestDistance) continue;
                Vec3 at = new Vec3(Math.floor(spot.x) + dx + 0.5, spot.y, Math.floor(spot.z) + dz + 0.5);
                if (!zone.allows(at.x, Math.min(spot.y, zone.ceiling()), at.z)) continue;
                best = at;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void shrink(Pokemon pokemon) {
        CompoundTag data = pokemon.getPersistentData();
        if (!data.contains(KEPT_SCALE)) return;
        pokemon.setScaleModifier(data.getFloat(KEPT_SCALE));
        data.remove(KEPT_SCALE);
        Set<String> forced = new HashSet<>(pokemon.getForcedAspects());
        forced.remove(PlantXurkitrees.STATUE);
        forced.remove(CRATER_ASPECT);
        pokemon.setForcedAspects(forced);
        pokemon.updateAspects();
    }

    private static void still(PokemonEntity entity, boolean still) {
        Pokemon pokemon = entity.getPokemon();
        Set<String> forced = new HashSet<>(pokemon.getForcedAspects());
        if (still ? !forced.add(PlantXurkitrees.STATUE) : !forced.remove(PlantXurkitrees.STATUE)) return;
        pokemon.setForcedAspects(forced);
        pokemon.updateAspects();
        entity.getEntityData().set(PokemonEntity.getASPECTS(), pokemon.getAspects());
    }

    private static void onLoad(Entity entity, ServerLevel level) {
        if (!(entity instanceof PokemonEntity pokemon)) return;
        if (pokemon.getTags().contains(FILLS) && pokemon.getPokemon().getPersistentData().contains(KEPT_SCALE)) {
            face(pokemon, pokemon.getYRot());
            pokemon.getEntityData().set(PokemonEntity.getASPECTS(), pokemon.getPokemon().getAspects());
            if (!pokemon.isBattling()) still(pokemon, true);
            pokemon.refreshDimensions();
            SETTLE.add(pokemon);
            return;
        }
        if (!pokemon.getTags().contains(TAG)) return;
        CraterLayout.Spot zone = CraterLayout.zone(pokemon.getBlockX(), pokemon.getBlockZ());
        boolean kept = giants(level, zone).stream()
                .anyMatch(c -> Math.abs(c.x() - pokemon.getBlockX()) <= 4 && Math.abs(c.z() - pokemon.getBlockZ()) <= 4);
        if (!kept) {
            DROP.add(pokemon);
            return;
        }
        face(pokemon, pokemon.getYRot());
        still(pokemon, true);
        SETTLE.add(pokemon);
    }

    static Vec3 onFloor(ServerLevel level, Vec3 at) {
        int x = Mth.floor(at.x), z = Mth.floor(at.z), top = Mth.floor(at.y);
        double floor = Double.NEGATIVE_INFINITY;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = top; y >= Math.max(level.getMinBuildHeight(), top - FLOOR_SEARCH); y--) {
                    pos.set(x + dx, y, z + dz);
                    VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                    if (shape.isEmpty()) continue;
                    floor = Math.max(floor, y + shape.max(Direction.Axis.Y));
                    break;
                }
            }
        }
        return floor == Double.NEGATIVE_INFINITY ? at : new Vec3(at.x, floor, at.z);
    }

    private static void settle(PokemonEntity pokemon) {
        if (pokemon.isRemoved() || !(pokemon.level() instanceof ServerLevel level)) return;
        Vec3 floor = onFloor(level, pokemon.position());
        if (Math.abs(floor.y - pokemon.getY()) > 0.05) {
            pokemon.moveTo(floor.x, floor.y, floor.z, pokemon.getYRot(), pokemon.getXRot());
        }
    }

    private static List<CraterLayout.Spot> giants(ServerLevel level, CraterLayout.Spot zone) {
        return CraterLayout.giants(level.getSeed(), zone).stream().filter(c -> BIG_PIECES.contains(c.kind() + 1)).toList();
    }

    private static void tick(MinecraftServer server) {
        drain(DROP, Entity::discard);
        drain(SETTLE, CraterGiants::settle);
        if (server.getTickCount() % EVERY != 0) return;
        ServerLevel level = server.getLevel(Wormholes.ULTRA_CRATER);
        if (level == null || level.players().isEmpty()) return;
        Set<Long> seen = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            CraterLayout.Spot zone = CraterLayout.zone(player.getBlockX(), player.getBlockZ());
            if (!seen.add(ChunkPos.asLong(zone.x(), zone.z()))) continue;
            for (CraterLayout.Spot crater : giants(level, zone)) stand(level, crater);
        }
    }

    private static void drain(List<PokemonEntity> queue, Consumer<PokemonEntity> action) {
        if (queue.isEmpty()) return;
        List<PokemonEntity> now = new ArrayList<>(queue);
        queue.clear();
        now.forEach(action);
    }

    private static void stand(ServerLevel level, CraterLayout.Spot crater) {
        int x = crater.x(), z = crater.z();
        ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
        if (!level.hasChunk(chunk.x, chunk.z) || !level.areEntitiesLoaded(chunk.toLong())) return;
        AABB around = new AABB(x - 4, level.getMinBuildHeight(), z - 4, x + 5, level.getMaxBuildHeight(), z + 5);
        if (!level.getEntitiesOfClass(PokemonEntity.class, around, e -> e.getTags().contains(TAG)).isEmpty()) return;
        long h = (long) x * 0x9E3779B97F4A7C15L ^ (long) z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        float scale = SMALLEST + (LARGEST - SMALLEST) * ((h >>> 40) & 0xFF) / 255f;
        float yaw = ((h >>> 16) & 0x3FF) * 360f / 1024f;
        try {
            PokemonEntity entity = PokemonProperties.Companion.parse("celesteela").createEntity(level);
            entity.getPokemon().setScaleModifier(scale);
            entity.refreshDimensions();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Vec3 floor = onFloor(level, new Vec3(x + 0.5, y, z + 0.5));
            entity.moveTo(floor.x, floor.y, floor.z, yaw, 0f);
            face(entity, yaw);
            for (AttributeInstance speed : new AttributeInstance[]{entity.getAttribute(Attributes.MOVEMENT_SPEED),
                    entity.getAttribute(Attributes.FLYING_SPEED)}) {
                if (speed != null) speed.setBaseValue(0);
            }
            entity.setNoGravity(true);
            entity.setInvulnerable(true);
            entity.setPersistenceRequired();
            entity.getEntityData().set(PokemonEntity.getUNBATTLEABLE(), true);
            entity.getEntityData().set(PokemonEntity.getHIDE_LABEL(), true);
            entity.addTag(TAG);
            still(entity, true);
            level.addFreshEntity(entity);
        } catch (RuntimeException e) {
            Wormholes.LOGGER.error("Couldn't stand a giant Celesteela in the Ultra Crater", e);
        }
    }
}
