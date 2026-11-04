package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.riding.RidingStyle;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.wormholes.WormholeConfig;
import kotlin.Unit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Cobblemon helpers for ride mounts: sending them out, putting them away and telling which one's which. */
public final class Mounts {
    private static final double COPY_SEARCH_RADIUS = 128;
    static boolean allowDismount;

    private Mounts() {}

    public static @Nullable Pokemon pokemonOf(@Nullable Entity entity) {
        return entity instanceof PokemonEntity mount && mount.getPokemon().isPlayerOwned() ? mount.getPokemon() : null;
    }

    public static boolean isAllowed(Pokemon pokemon) {
        ResourceLocation species = pokemon.getSpecies().getResourceIdentifier();
        if (!WormholeConfig.get().allowedMounts.contains(species.toString())) return false;
        return !species.getPath().equals("necrozma")
                || pokemon.getAspects().contains("dusk-fusion") || pokemon.getAspects().contains("dawn-fusion");
    }

    public static @Nullable Pokemon findMount(ServerPlayer player) {
        Pokemon ridden = pokemonOf(player.getVehicle());
        if (ridden != null && isAllowed(ridden)) return ridden;
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon != null && !pokemon.isFainted() && isAllowed(pokemon)) return pokemon;
        }
        return null;
    }

    public static boolean canFly(Pokemon pokemon) {
        Map<RidingStyle, ?> behaviours = pokemon.getRiding().getBehaviours();
        return behaviours != null && behaviours.get(RidingStyle.AIR) != null;
    }

    public static RideLegend kindOf(@Nullable Pokemon pokemon) {
        if (pokemon == null) return RideLegend.NONE;
        return switch (pokemon.getSpecies().getResourceIdentifier().getPath()) {
            case "solgaleo" -> RideLegend.SOLGALEO;
            case "lunala" -> RideLegend.LUNALA;
            case "necrozma" -> pokemon.getAspects().contains("dusk-fusion") ? RideLegend.SOLGALEO
                    : pokemon.getAspects().contains("dawn-fusion") ? RideLegend.LUNALA : RideLegend.NECROZMA;
            default -> RideLegend.NONE;
        };
    }

    static @Nullable PokemonEntity mount(ServerPlayer player, Pokemon pokemon, ServerLevel level, Vec3 pos, float yaw) {
        removeCopies(level, pokemon, pos);
        PokemonEntity entity = pokemon.sendOut(level, pos, null, e -> {
            e.setYRot(yaw);
            e.setYHeadRot(yaw);
            e.setYBodyRot(yaw);
            return Unit.INSTANCE;
        });
        if (entity != null) seat(player, entity);
        return entity;
    }

    static void seat(ServerPlayer player, PokemonEntity entity) {
        if (entity.getBeamMode() != 0) entity.setBeamMode(0);
        if (player.getVehicle() != null && player.getVehicle() != entity) dismount(player);
        if (player.getVehicle() != entity) unguarded(() -> player.startRiding(entity, true));
        if (!entity.hasPassenger(player) && player.getVehicle() == entity) dismount(player);
    }

    static void seatPassengers(List<ServerPlayer> passengers, PokemonEntity entity) {
        for (ServerPlayer p : passengers) {
            if (p.getVehicle() == entity || p.level() != entity.level()) continue;
            if (p.getVehicle() != null) dismount(p);
            unguarded(() -> p.startRiding(entity, true));
        }
    }

    static void dismount(ServerPlayer player) {
        unguarded(player::stopRiding);
    }

    private static void unguarded(Runnable action) {
        allowDismount = true;
        try {
            action.run();
        } finally {
            allowDismount = false;
        }
    }

    static void putAway(Pokemon pokemon, ServerPlayer near) {
        PokemonEntity entity = pokemon.getEntity();
        if (entity != null) {
            pokemon.recall();
            if (!entity.isRemoved()) entity.discard();
        }
        removeCopies(near.serverLevel(), pokemon, near.position());
    }

    static void removeCopies(ServerLevel level, Pokemon pokemon, Vec3 pos) {
        UUID id = pokemon.getUuid();
        level.getEntitiesOfClass(PokemonEntity.class, new AABB(pos, pos).inflate(COPY_SEARCH_RADIUS),
                e -> e.getPokemon().getUuid().equals(id)).forEach(Entity::discard);
    }
}
