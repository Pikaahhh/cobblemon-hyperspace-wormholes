package dev.hyperspacewormholes.wormholes;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import kotlin.Unit;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pokemon from wormholes have a chance to drop a Z-Crystal of one of their types when you catch, beat or kill them.
 * Doesn't apply to legendaries, mythicals, Ultra Beasts or Necrozma.
 */
public final class ZCrystals {
    private static final String MARK = "hyperspace_wormholes_z_crystal";
    private static final Set<String> EXCLUDED_LABELS = Set.of("legendary", "mythical", "ultra_beast");
    private static final Map<String, String> CRYSTALS = Map.ofEntries(
            Map.entry("normal", "normalium_z"), Map.entry("fire", "firium_z"), Map.entry("water", "waterium_z"),
            Map.entry("electric", "electrium_z"), Map.entry("grass", "grassium_z"), Map.entry("ice", "icium_z"),
            Map.entry("fighting", "fightinium_z"), Map.entry("poison", "poisonium_z"), Map.entry("ground", "groundium_z"),
            Map.entry("flying", "flyinium_z"), Map.entry("psychic", "psychium_z"), Map.entry("bug", "buginium_z"),
            Map.entry("rock", "rockium_z"), Map.entry("ghost", "ghostium_z"), Map.entry("dragon", "dragonium_z"),
            Map.entry("dark", "darkinium_z"), Map.entry("steel", "steelium_z"), Map.entry("fairy", "fairium_z"));

    public static void mark(Pokemon pokemon) {
        Species species = pokemon.getSpecies();
        if (species.getResourceIdentifier().getPath().equals("necrozma")) return;
        if (Collections.disjoint(species.getLabels(), EXCLUDED_LABELS)) pokemon.getPersistentData().putBoolean(MARK, true);
    }

    public static void register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, event -> {
            ItemStack crystal = roll(event.getPokemon(), event.getPlayer().getRandom());
            if (crystal != null) give(event.getPlayer(), crystal);
            return Unit.INSTANCE;
        });
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.NORMAL, event -> {
            BattlePokemon killed = event.getKilled();
            if (killed.getActor().getType() != ActorType.WILD) return Unit.INSTANCE;
            List<ServerPlayer> players = event.getBattle().getPlayers();
            if (players.isEmpty()) return Unit.INSTANCE;
            ItemStack crystal = roll(killed.getEffectedPokemon(), players.get(0).getRandom());
            if (crystal != null) for (ServerPlayer player : players) give(player, crystal.copy());
            return Unit.INSTANCE;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof PokemonEntity pokemon) || !(source.getEntity() instanceof ServerPlayer)) return;
            if (pokemon.isBattling() || !pokemon.getPokemon().isWild()) return;
            ItemStack crystal = roll(pokemon.getPokemon(), pokemon.getRandom());
            if (crystal != null) pokemon.spawnAtLocation(crystal);
        });
    }

    private static @Nullable ItemStack roll(Pokemon pokemon, RandomSource random) {
        if (!pokemon.getPersistentData().getBoolean(MARK)) return null;
        pokemon.getPersistentData().remove(MARK);
        if (random.nextFloat() >= 0.1f) return null;
        ElementalType secondary = pokemon.getSecondaryType();
        ElementalType type = secondary != null && random.nextBoolean() ? secondary : pokemon.getPrimaryType();
        String crystal = CRYSTALS.get(type.getName().toLowerCase(Locale.ROOT));
        if (crystal == null) return null;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("mega_showdown", crystal));
        return item == Items.AIR ? null : new ItemStack(item);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
