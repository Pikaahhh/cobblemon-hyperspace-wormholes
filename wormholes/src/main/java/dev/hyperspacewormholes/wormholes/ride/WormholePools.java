package dev.hyperspacewormholes.wormholes.ride;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hyperspacewormholes.wormholes.PokemonTags;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Pokemon past each Ultra Space Wilds wormhole colour, from data/hyperspace_wormholes/wormhole_pools/(red, green,
 * yellow, blue).json: {"pokemon": ["swellow", {"pokemon": "dragonite", "weight": 3, "level": "60-70"}],
 *  "legendaries": ["articuno", {"pokemon": "rayquaza", "party": ["kyogre", "groudon"]}]}.
 * An entry is a species name (with any aspects after it, as in Pokemon tags) or an object with optional weight (relative,
 * default 1, 0 or less leaves it out), level (one number or a range, default: the mod's own level) and party (species the
 * player needs in their party for that one to come). Like any other data file, a datapack's file for a colour replaces the
 * mod's whole file; nothing merges. Ultra Beast worlds aren't covered. The Wilds tags these replace (wilds_cliff and
 * so on) are no longer read, and a datapack still shipping one gets a warning in the log.
 */
public final class WormholePools implements SimpleSynchronousResourceReloadListener {
    private static final String DIRECTORY = "wormhole_pools";
    private static final List<String> OLD_TAGS = List.of("wilds_cliff", "wilds_crag", "wilds_cave", "wilds_water");
    private static Map<String, Pool> pools = Map.of();

    public record Entry(String pokemon, double weight, int minLevel, int maxLevel, List<String> party) {
        public int level(int fallback, RandomSource random) {
            if (minLevel <= 0) return fallback;
            return maxLevel > minLevel ? minLevel + random.nextInt(maxLevel - minLevel + 1) : minLevel;
        }
    }

    public record Pool(List<Entry> pokemon, List<Entry> legendaries) {
        static final Pool EMPTY = new Pool(List.of(), List.of());
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new WormholePools());
    }

    public static String colourName(WormholeType type) {
        return switch (type) {
            case RED -> "red";
            case GREEN -> "green";
            case YELLOW -> "yellow";
            case WATER -> "blue";
            default -> type.name().toLowerCase(Locale.ROOT);
        };
    }

    /** The four Wilds colours' pools (red, green, yellow, blue), as the Item Analyser draws from all of them. */
    public static List<Pool> wilds() {
        return List.of(WormholeType.RED, WormholeType.GREEN, WormholeType.YELLOW, WormholeType.WATER).stream()
                .map(type -> pools.getOrDefault(colourName(type), Pool.EMPTY)).toList();
    }

    public static Pool of(ResourceKey<Level> wildsWorld) {
        return pools.getOrDefault(colourName(WormholeType.ofWilds(wildsWorld)), Pool.EMPTY);
    }

    @Override
    public ResourceLocation getFabricId() {
        return Wormholes.id("wormhole_pools");
    }

    @Override
    public Collection<ResourceLocation> getFabricDependencies() {
        return List.of(Wormholes.id("pokemon_tags"));
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<String, Pool> loaded = new HashMap<>();
        manager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json")).forEach((file, resource) -> {
            if (!file.getNamespace().equals(Wormholes.MOD_ID)) return;
            String path = file.getPath();
            String colour = path.substring(DIRECTORY.length() + 1, path.length() - ".json".length());
            Map<String, Entry> pokemon = new LinkedHashMap<>(), legendaries = new LinkedHashMap<>();
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                read(json, "pokemon", pokemon, file);
                read(json, "legendaries", legendaries, file);
            } catch (IOException | RuntimeException e) {
                Wormholes.LOGGER.error("Couldn't read wormhole pool {} from {}", file, resource.sourcePackId(), e);
            }
            loaded.put(colour, new Pool(weighted(pokemon), weighted(legendaries)));
        });
        pools = loaded;
        for (String tag : OLD_TAGS) {
            if (!PokemonTags.get(Wormholes.id(tag)).isEmpty() || !PokemonTags.get(Wormholes.id(tag + "_legendaries")).isEmpty()) {
                Wormholes.LOGGER.warn("The Pokémon tag {}:{} isn't used since 2.1.0; the Wilds Pokémon are in data/{}/{}/<colour>.json",
                        Wormholes.MOD_ID, tag, Wormholes.MOD_ID, DIRECTORY);
            }
        }
    }

    private static void read(JsonObject json, String list, Map<String, Entry> into, ResourceLocation file) {
        if (!json.has(list)) return;
        JsonArray entries = json.getAsJsonArray(list);
        for (JsonElement element : entries) {
            Entry entry = entry(element);
            if (entry == null) {
                Wormholes.LOGGER.warn("Skipping a {} entry in {}: {}", list, file, element);
                continue;
            }
            into.put(entry.pokemon(), entry);
        }
    }

    private static @Nullable Entry entry(JsonElement element) {
        if (element.isJsonPrimitive()) return new Entry(clean(element.getAsString()), 1, 0, 0, List.of());
        if (!element.isJsonObject()) return null;
        JsonObject o = element.getAsJsonObject();
        if (!o.has("pokemon")) return null;
        String pokemon = clean(o.get("pokemon").getAsString());
        if (pokemon.isEmpty()) return null;
        double weight = o.has("weight") ? o.get("weight").getAsDouble() : 1;
        int min = 0, max = 0;
        if (o.has("level")) {
            String level = o.get("level").getAsString().trim();
            int dash = level.indexOf('-');
            min = Integer.parseInt((dash < 0 ? level : level.substring(0, dash)).trim());
            max = dash < 0 ? min : Integer.parseInt(level.substring(dash + 1).trim());
            if (min < 1 || max < min) return null;
        }
        List<String> party = new ArrayList<>();
        if (o.has("party")) for (JsonElement p : o.getAsJsonArray("party")) party.add(clean(p.getAsString()));
        return new Entry(pokemon, weight, min, max, List.copyOf(party));
    }

    private static String clean(String text) {
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static List<Entry> weighted(Map<String, Entry> entries) {
        return entries.values().stream().filter(e -> e.weight() > 0 && Double.isFinite(e.weight())).toList();
    }
}
