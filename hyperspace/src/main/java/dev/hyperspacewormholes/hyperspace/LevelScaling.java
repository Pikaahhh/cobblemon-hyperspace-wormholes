package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.Random;
import java.util.function.Consumer;

/**
 * Levels in Hyperspace. Your Pokemon get bumped up to the trip's level range while you're there and put back after,
 * stuff you catch there keeps sensible levels, and Cobblemon's level cap goes up to 200.
 */
public class LevelScaling {
    public static int maxLevel = 200;
    public static int normalCap = 100;
    public static int legendaryCaughtMin = 80;

    private static final String BOOST = "hyperspace_boost";

    private static final Random random = new Random();

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            if (Cobblemon.INSTANCE.getConfig().getMaxPokemonLevel() < maxLevel) Cobblemon.INSTANCE.getConfig().setMaxPokemonLevel(maxLevel);
        });

        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(Priority.LOWEST, event -> {
            Pokemon mon = event.getPokemon();
            if (ownerInHyperspace(mon)) {
                event.cancel();
                return Unit.INSTANCE;
            }
            int maxXp = mon.getExperienceGroup().getExperience(normalCap);
            if (mon.getLevel() >= normalCap || mon.getExperience() >= maxXp) event.cancel();
            else event.setExperience(Math.min(event.getExperience(), maxXp - mon.getExperience()));
            return Unit.INSTANCE;
        });

        CobblemonEvents.EXPERIENCE_CANDY_USE_PRE.subscribe(Priority.LOWEST, event -> {
            if (Hyperspace.inHyperspace(event.getPlayer())) event.cancel();
            return Unit.INSTANCE;
        });

        CobblemonEvents.LEVEL_UP_EVENT.subscribe(Priority.LOWEST, event -> {
            if (ownerInHyperspace(event.getPokemon())) event.setNewLevel(event.getOldLevel());
            else if (event.getNewLevel() > normalCap) event.setNewLevel(Math.max(event.getOldLevel(), normalCap));
            return Unit.INSTANCE;
        });

        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, event -> {
            ServerPlayer player = event.getPlayer();
            Pokemon pokemon = event.getPokemon();
            if (Hyperspace.inHyperspace(player)) caughtInHyperspace(player, pokemon);
            if (Hyperspace.inHyperspace(player) && pokemon.getShiny()) HyperspaceAdvancements.shiny(player);
            HyperspaceLegendaries.captured(player, pokemon);
            if (pokemon.getAbility().getName().equals(Spawns.GUARD)) pokemon.updateAbility(pokemon.rollAbility());
            return Unit.INSTANCE;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            forAll(handler.getPlayer(), LevelScaling::restoreOld);
            if (!Hyperspace.inHyperspace(handler.getPlayer())) forAll(handler.getPlayer(), LevelScaling::unboost);
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            if (origin.dimension().equals(Hyperspace.HYPERSPACE)) forAll(player, LevelScaling::unboost);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!Hyperspace.inHyperspace(newPlayer)) forAll(newPlayer, LevelScaling::unboost);
        });
    }

    private static boolean ownerInHyperspace(Pokemon mon) {
        ServerPlayer owner = mon.getOwnerPlayer();
        return owner != null && Hyperspace.inHyperspace(owner);
    }

    private static void caughtInHyperspace(ServerPlayer player, Pokemon mon) {
        // the Hoopa event's Hoopa keeps its level 70
        if (HoopaEvent.on(player)) return;
        int stars = Spawns.stars(WorldData.get(player.server).trips.get(player.getUUID()));
        int min = DonutBoosts.minCaughtLevel(stars);
        int level = min + random.nextInt(DonutBoosts.maxCaughtLevel(stars) - min + 1);
        if (Spawns.isLegend(mon.getSpecies())) level = Math.max(level, legendaryCaughtMin);
        setLevel(mon, Mth.clamp(level, 1, normalCap));
    }

    public static void boost(ServerPlayer player, int stars) {
        stars = Math.max(1, stars);
        int min = DonutBoosts.minLevel(stars), max = DonutBoosts.maxLevel(stars);
        for (Pokemon mon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (mon.getPersistentData().contains(BOOST)) continue;
            int level = min + random.nextInt(max - min + 1);
            if (level <= mon.getLevel()) continue;
            CompoundTag tag = new CompoundTag();
            tag.putInt("xp", mon.getExperience());
            mon.getPersistentData().put(BOOST, tag);
            setLevel(mon, level);
        }
    }

    private static void forAll(ServerPlayer player, Consumer<Pokemon> action) {
        try {
            for (Pokemon mon : Cobblemon.INSTANCE.getStorage().getParty(player)) action.accept(mon);
            for (Pokemon mon : Cobblemon.INSTANCE.getStorage().getPC(player)) action.accept(mon);
        } catch (Exception e) {
            Hyperspace.LOGGER.error("Couldn't put levels back for {}", player.getName().getString(), e);
        }
    }

    private static void unboost(Pokemon mon) {
        if (!mon.getPersistentData().contains(BOOST)) return;
        int xp = mon.getPersistentData().getCompound(BOOST).getInt("xp");
        mon.getPersistentData().remove(BOOST);
        mon.setExperienceAndUpdateLevel(xp);
        mon.onChange(null);
    }

    private static void setLevel(Pokemon mon, int level) {
        mon.setLevel(level);
        mon.setExperienceAndUpdateLevel(mon.getExperienceGroup().getExperience(level));
        mon.onChange(null);
    }

    private static void restoreOld(Pokemon mon) {
        if (!mon.getPersistentData().contains("hyperspace_levels")) return;
        CompoundTag tag = mon.getPersistentData().getCompound("hyperspace_levels");
        mon.getPersistentData().remove("hyperspace_levels");
        if (tag.getBoolean("in_hyperspace") && tag.contains("normal_xp")) {
            setLevel(mon, Mth.clamp(mon.getExperienceGroup().getLevel(tag.getInt("normal_xp")), 1, normalCap));
        } else {
            mon.onChange(null);
        }
    }
}
