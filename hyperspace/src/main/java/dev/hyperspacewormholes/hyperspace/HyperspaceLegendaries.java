package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kotlin.Unit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Rules for Hyperspace bosses (legendaries and Megas). Only the person who summoned one can fight it or throw balls at
 * it, beating a Mega gives its Mega Stone, and if you catch it, it goes back to its normal form.
 */
public class HyperspaceLegendaries {
    private static final String STONES = "mega_showdown/mega";
    private static final String MEGA_REVERT = "mega_evolution=none";
    private static final int[] REVERT_AGAIN = {20, 100};
    private static final List<Revert> reverting = new ArrayList<>();
    private static final Set<Pokemon> fainted = Collections.newSetFromMap(new WeakHashMap<>());

    private record Revert(Pokemon pokemon, List<String> properties, int at) {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(HyperspaceLegendaries::tick);

        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL, event -> {
            for (BattleActor actor : event.getBattle().getActors()) {
                for (BattlePokemon pokemon : actor.getPokemonList()) {
                    UUID owner = Spawns.isBoss(pokemon.getEffectedPokemon()) ? Spawns.owner(pokemon.getEffectedPokemon()) : null;
                    if (owner == null) continue;
                    for (ServerPlayer player : event.getBattle().getPlayers()) {
                        if (player.getUUID().equals(owner)) continue;
                        event.cancel();
                        player.sendSystemMessage(Component.literal("That Pokemon belongs to someone else!").withStyle(ChatFormatting.RED));
                        return Unit.INSTANCE;
                    }
                }
            }
            return Unit.INSTANCE;
        });

        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.NORMAL, event -> {
            Pokemon pokemon = event.getPokemon().getPokemon();
            UUID owner = Spawns.isBoss(pokemon) ? Spawns.owner(pokemon) : null;
            Entity thrower = event.getPokeBall().getOwner();
            if (owner != null && thrower != null && !owner.equals(thrower.getUUID())) event.cancel();
            return Unit.INSTANCE;
        });

        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.NORMAL, event -> {
            BattlePokemon killed = event.getKilled();
            if (killed.getActor().getType() != ActorType.WILD) return Unit.INSTANCE;
            Pokemon pokemon = killed.getEffectedPokemon();
            fainted.add(pokemon);
            Item stone = megaStone(event.getBattle().getPlayers(), pokemon);
            if (stone != null) {
                for (ServerPlayer player : event.getBattle().getPlayers()) Tasks.give(player, new ItemStack(stone));
            }
            return Unit.INSTANCE;
        });
    }

    public static boolean faintedInBattle(Pokemon pokemon) {
        return fainted.contains(pokemon);
    }

    private static Item megaStone(List<ServerPlayer> players, Pokemon pokemon) {
        String aspect = Spawns.megaBoss(pokemon);
        if (aspect == null || players.isEmpty()) return null;
        ResourceManager resources = players.get(0).server.getResourceManager();
        String species = pokemon.getSpecies().getName();
        for (Map.Entry<ResourceLocation, Resource> entry : resources.listResources(STONES, path -> path.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                boolean named = false;
                for (JsonElement name : json.getAsJsonArray("pokemons")) named |= name.getAsString().equalsIgnoreCase(species);
                if (!named) continue;
                JsonArray applies = json.getAsJsonObject("aspect_conditions").getAsJsonObject("apply").getAsJsonArray("aspects");
                boolean matches = false;
                for (JsonElement apply : applies) matches |= apply.getAsString().equals("mega_evolution=" + aspect);
                if (!matches) continue;
                String path = entry.getKey().getPath();
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(), path.substring(STONES.length() + 1, path.length() - 5));
                if (BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.get(id);
            } catch (Exception e) {
                Hyperspace.LOGGER.warn("Couldn't read mega stone file {}", entry.getKey(), e);
            }
        }
        return null;
    }

    public static void captured(ServerPlayer player, Pokemon pokemon) {
        CompoundTag data = pokemon.getPersistentData();
        List<String> reverts = new ArrayList<>();
        String strongest = data == null ? "" : data.getString(Spawns.REVERT);
        if (!strongest.isEmpty()) reverts.add(strongest);
        if (!reverts.contains(MEGA_REVERT) && pokemon.getAspects().stream().anyMatch(aspect -> aspect.startsWith("mega"))) {
            reverts.add(MEGA_REVERT);
        }
        if (!reverts.isEmpty()) {
            revert(pokemon, reverts);
            int now = player.server.getTickCount();
            for (int delay : REVERT_AGAIN) reverting.add(new Revert(pokemon, reverts, now + delay));
        }
        if (Spawns.isBoss(pokemon)) {
            data.remove(Spawns.BOSS);
            data.remove(Spawns.OWNER);
            data.remove(Spawns.REVERT);
            pokemon.setScaleModifier(1f);
            pokemon.setCurrentHealth(Math.min(pokemon.getCurrentHealth(), pokemon.getMaxHealth()));
        }
    }

    private static void revert(Pokemon pokemon, List<String> reverts) {
        for (String properties : reverts) PokemonProperties.Companion.parse(properties).apply(pokemon);
    }

    private static void tick(MinecraftServer server) {
        reverting.removeIf(revert -> {
            if (revert.at() > server.getTickCount()) return false;
            revert(revert.pokemon(), revert.properties());
            return true;
        });
    }
}
