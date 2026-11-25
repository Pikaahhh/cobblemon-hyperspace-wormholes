package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;
import com.cobblemon.mod.common.net.messages.client.effect.RunPosableMoLangPacket;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import com.cobblemon.mod.common.net.messages.client.sound.UnvalidatedPlaySoundS2CPacket;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.mixin.ChunkMapAccessor;
import kotlin.Unit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Pokemon past a wormhole that is one player's alone: in the Ultra worlds every player on a trip gets their own
 * (WormholeDestinations.spawnWild), and whose it is is saved on it (Wormholes.OWN_PLAYER). The same goes for what
 * belongs to it: its little wormhole, the lightning that wakes it, a ball or arrow once it hits it, what it drops.
 * <p>
 * Only its player's client is sent it (hiddenFrom, BossPrizeTrackingMixin, which keeps the Megalopolis and Prison
 * Bottle catch copies to their players too, NecrozmaFight.prizeOwner). On the server two things are apart when one is a
 * player's own and the other isn't on that player's side: the player, what they own, threw, lit or ride, and their
 * other own things (side). Apart things are left out of each other's entity searches, collisions, sight and targeting,
 * can't push, hurt or affect each other, and no packet from or to another client can name one (the Own* mixins). A
 * battle with it is its player's alone, and nobody can watch it.
 * <p>
 * While its scope is open (only: round its tick, its sounds, its death drops, its player's blows, and what the mod
 * plays for it) sounds and particles the server would send to everyone near go only to its player (heard), and the
 * items and experience that appear are its player's own. Nothing is filtered while none is loaded (any).
 */
public final class OwnEncounters {
    private static final Set<Entity> LOADED = ConcurrentHashMap.newKeySet();
    private static @Nullable UUID scope;
    private static @Nullable Thread scopeThread;
    private static boolean ticking;

    /** ChunkMap's record of who is sent an entity (OwnTrackedEntityMixin). */
    public interface Tracked {
        void hyperspace_wormholes$retrack(List<ServerPlayer> players);
    }

    private OwnEncounters() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (owner(entity) != null) LOADED.add(entity);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> LOADED.remove(entity));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LOADED.clear();
            scope = null;
        });
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.HIGHEST, event -> {
            for (UUID player : event.getBattle().getPlayerUUIDs()) {
                if (!mayJoin(event.getBattle(), player)) event.cancel();
            }
            return Unit.INSTANCE;
        });
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.HIGHEST, event -> {
            if (apart(event.getPokeBall(), event.getPokemon())) event.cancel();
            return Unit.INSTANCE;
        });
    }

    static void give(Entity entity, UUID owner) {
        entity.setAttached(Wormholes.OWN_PLAYER, owner);
        if (entity instanceof ItemEntity item) item.setTarget(owner);
        if (entity instanceof PokemonEntity pokemon) pokemon.setCountsTowardsSpawnCap(false);
        if (entity.level() instanceof ServerLevel level && level.getEntity(entity.getId()) == entity) {
            LOADED.add(entity);
            Object tracked = ((ChunkMapAccessor) level.getChunkSource().chunkMap).hyperspace_wormholes$entityMap().get(entity.getId());
            if (tracked instanceof Tracked t) t.hyperspace_wormholes$retrack(level.players());
        }
    }

    /** Makes entity the same player's own as of is, if of is anyone's. */
    static void share(Entity entity, Entity of) {
        UUID owner = owner(of);
        if (owner != null) give(entity, owner);
    }

    public static @Nullable UUID owner(Entity entity) {
        return entity.getAttached(Wormholes.OWN_PLAYER);
    }

    private static boolean any() {
        return !LOADED.isEmpty();
    }

    static List<PokemonEntity> loaded() {
        List<PokemonEntity> pokemon = new ArrayList<>();
        for (Entity entity : LOADED) {
            if (entity instanceof PokemonEntity p) pokemon.add(p);
        }
        return pokemon;
    }

    /** Whose side entity is on: the player it is, is the own of, belongs to, came from or carries, or null. */
    private static @Nullable UUID side(Entity entity) {
        if (entity instanceof Player) return entity.getUUID();
        UUID owner = owner(entity);
        if (owner != null) return owner;
        if (entity instanceof PokemonEntity pokemon && pokemon.getOwnerUUID() != null) return pokemon.getOwnerUUID();
        Entity from = entity instanceof TraceableEntity traceable ? traceable.getOwner()
                : entity instanceof OwnableEntity ownable ? ownable.getOwner() : null;
        if (from instanceof PokemonEntity pokemon && pokemon.getOwnerUUID() != null) return pokemon.getOwnerUUID();
        if (!(from instanceof Player)) from = entity.getControllingPassenger();
        if (!(from instanceof Player)) from = entity.getFirstPassenger();
        return from instanceof Player ? from.getUUID() : null;
    }

    /** Whether entity is kept from viewer's client and throws: another player's own, or another player's catch copy. */
    public static boolean hiddenFrom(Entity entity, @Nullable Entity viewer) {
        UUID owner = owner(entity);
        if (owner == null) owner = NecrozmaFight.prizeOwner(entity);
        return owner != null && (viewer == null || !owner.equals(side(viewer)));
    }

    /** Whether one of the two is a player's own and the other isn't on that player's side. */
    public static boolean apart(@Nullable Entity a, @Nullable Entity b) {
        if (a == null || b == null || a == b || !any()) return false;
        UUID ownerA = owner(a), ownerB = owner(b);
        return ownerA != null && !ownerA.equals(side(b)) || ownerB != null && !ownerB.equals(side(a));
    }

    public static boolean unhurt(Entity entity, DamageSource source) {
        if (!any()) return false;
        Entity from = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        return from != null ? apart(entity, from) : owner(entity) != null && source.is(DamageTypeTags.IS_EXPLOSION);
    }

    /** The list without what is apart from viewer. */
    public static <T extends Entity> List<T> without(@Nullable Entity viewer, List<T> entities) {
        if (viewer == null || !any() || viewer.level().isClientSide()) return entities;
        for (T entity : entities) {
            if (!apart(viewer, entity)) continue;
            List<T> kept = new ArrayList<>(entities);
            kept.removeIf(e -> apart(viewer, e));
            return kept;
        }
        return entities;
    }

    /** The player within range (negative for anywhere) whose own entity is, or the nearest to at if it is nobody's. */
    static @Nullable Player nearest(Entity entity, ServerLevel level, Vec3 at, double range) {
        UUID owner = owner(entity);
        if (owner == null) return level.getNearestPlayer(at.x, at.y, at.z, range, false);
        Player player = level.getPlayerByUUID(owner);
        return player == null || player.isSpectator() || range >= 0 && player.distanceToSqr(at) >= range * range ? null : player;
    }

    private static boolean mayJoin(PokemonBattle battle, UUID player) {
        for (BattleActor actor : battle.getActors()) {
            for (BattlePokemon pokemon : actor.getPokemonList()) {
                PokemonEntity entity = pokemon.getEntity();
                UUID owner = entity == null ? null : owner(entity);
                if (owner != null && !owner.equals(player)) return false;
            }
        }
        return true;
    }

    public static boolean mayWatch(@Nullable PokemonBattle battle, ServerPlayer watcher) {
        return battle == null || mayJoin(battle, watcher.getUUID());
    }

    /** Runs with entity's scope open, if it is anyone's own. */
    public static void only(@Nullable Entity entity, Runnable run) {
        UUID owner = entity == null || entity.level().isClientSide() ? null : owner(entity);
        if (owner == null) {
            run.run();
            return;
        }
        UUID before = scope();
        open(owner);
        try {
            run.run();
        } finally {
            scope = before;
        }
    }

    private static void open(UUID owner) {
        scope = owner;
        scopeThread = Thread.currentThread();
    }

    public static @Nullable UUID scope() {
        return scope != null && Thread.currentThread() == scopeThread ? scope : null;
    }

    public static boolean heard(ServerPlayer player) {
        UUID only = scope();
        return only == null || only.equals(player.getUUID());
    }

    public static void tickStarts(Entity entity) {
        ticking = true;
        scope = null;
        UUID owner = any() ? owner(entity) : null;
        if (owner != null) open(owner);
    }

    public static void tickEnds() {
        ticking = false;
        scope = null;
    }

    /** A projectile hits someone's own: from then on it is theirs too. */
    public static void join(Entity projectile, Entity target) {
        UUID owner = owner(target);
        if (owner == null || target.level().isClientSide() || owner.equals(owner(projectile))) return;
        give(projectile, owner);
        if (ticking) open(owner);
    }

    /** What appears while a scope is open. */
    public static void appears(Entity entity) {
        UUID only = scope();
        if (only != null && (entity instanceof ItemEntity || entity instanceof ExperienceOrb) && owner(entity) == null) give(entity, only);
    }

    /** Whether a Cobblemon packet is about something hidden from player, or is sent while another player's scope is open. */
    public static boolean withheld(ServerPlayer player, Object packet) {
        if (!any()) return false;
        if (packet instanceof PlayPosableAnimationPacket p) return hidden(player, p.getEntityId());
        if (packet instanceof RunPosableMoLangPacket p) return hidden(player, p.getEntityId());
        if (packet instanceof SpawnSnowstormEntityParticlePacket p) {
            return hidden(player, p.getSourceEntityId()) || p.getTargetedEntityId() != null && hidden(player, p.getTargetedEntityId());
        }
        return (packet instanceof SpawnSnowstormParticlePacket || packet instanceof UnvalidatedPlaySoundS2CPacket) && !heard(player);
    }

    private static boolean hidden(ServerPlayer player, int id) {
        Entity entity = player.level().getEntity(id);
        return entity != null && hiddenFrom(entity, player);
    }
}
