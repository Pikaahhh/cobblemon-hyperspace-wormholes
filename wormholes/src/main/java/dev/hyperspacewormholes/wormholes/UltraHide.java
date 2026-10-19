package dev.hyperspacewormholes.wormholes;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * The Ultra Hide, which you get for catching, beating or killing an Ultra Beast. Also keeps Ultra Beasts out of
 * natural spawns.
 */
public final class UltraHide {
    public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, Wormholes.id("ultra_hide"),
            new Item(new Item.Properties().rarity(Rarity.EPIC)));

    public static void register() {
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.HIGHEST, event -> {
            if (event.getEntity().getPokemon().getSpecies().getLabels().contains("ultra_beast")) event.cancel();
            return Unit.INSTANCE;
        });
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, event -> {
            if (fromUltraBeast(event.getPokemon())) give(event.getPlayer());
            return Unit.INSTANCE;
        });
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.NORMAL, event -> {
            BattlePokemon killed = event.getKilled();
            if (killed.getActor().getType() == ActorType.WILD && fromUltraBeast(killed.getEffectedPokemon())) {
                for (ServerPlayer player : event.getBattle().getPlayers()) give(player);
            }
            return Unit.INSTANCE;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof PokemonEntity pokemon) || !(source.getEntity() instanceof ServerPlayer)) return;
            if (pokemon.isBattling() || !pokemon.getPokemon().isWild() || !fromUltraBeast(pokemon.getPokemon())) return;
            pokemon.spawnAtLocation(new ItemStack(ITEM, 1 + pokemon.getRandom().nextInt(2)));
        });
    }

    private static boolean fromUltraBeast(Pokemon pokemon) {
        return pokemon.getSpecies().getLabels().contains("ultra_beast")
                || pokemon.getSpecies().getResourceIdentifier().getPath().equals("necrozma");
    }

    private static void give(ServerPlayer player) {
        ItemStack stack = new ItemStack(ITEM, 1 + player.getRandom().nextInt(2));
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
