package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.ShinyChanceCalculationEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.PokemonSizeCategory;
import com.cobblemon.mod.common.pokemon.Species;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.PokemonTags;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Which wild Pokemon is waiting past each tunnel wormhole, going by its colour (normal ones from the wormhole level
 * tags, legendaries or Ultra Beasts), plus its level and shiny and alpha odds from its rings. Species Cobblemon hasn't
 * implemented are skipped, so you never get a stand-in model.
 */
public final class WormholePokemon {
    private static final int SHINY_BOOST_PER_RING = 3;
    private static final float ALPHA_BASE = 0.02f;
    private static final float ALPHA_BOOST_PER_RING = 1.5f;
    private static final int[] LEVELS = {45, 65, 80};
    private static final int LEGENDARY_LEVEL = 75;
    private static final float ANALYSER_SHINY_RATE = 16f;
    private static final float SKY_SHINY_RATE = 8f;
    private static final Set<String> EVOLVED_ULTRA_BEASTS = Set.of("naganadel");

    private record Candidate(String properties, List<String> types) {}

    private WormholePokemon() {}

    public static @Nullable String ultraBeastOfType(String type, RandomSource random) {
        String wanted = type.toLowerCase(Locale.ROOT);
        List<Candidate> pool = ultraBeasts().stream().filter(c -> c.types().contains(wanted)).toList();
        if (pool.isEmpty()) return null;
        Candidate chosen = pool.get(random.nextInt(pool.size()));
        return chosen.properties() + " level=" + LEGENDARY_LEVEL + " shiny=" + wildShiny(random);
    }

    public static @Nullable String regularOfType(String type, RandomSource random) {
        String wanted = type.toLowerCase(Locale.ROOT);
        List<String> all = new ArrayList<>(), matching = new ArrayList<>();
        for (int difficulty = 1; difficulty <= LEVELS.length; difficulty++) {
            for (Candidate c : regular(difficulty)) {
                String spawn = c.properties() + " level=" + LEVELS[difficulty - 1];
                all.add(spawn);
                if (c.types().contains(wanted)) matching.add(spawn);
            }
        }
        List<String> pool = matching.isEmpty() ? all : matching;
        if (pool.isEmpty()) return null;
        String chosen = pool.get(random.nextInt(pool.size()));
        return chosen + " shiny=" + wildShiny(random);
    }

    public static @Nullable String ultraBeastNamed(String species, RandomSource random) {
        Candidate chosen = candidate(species);
        if (chosen == null) return null;
        return chosen.properties() + " level=" + LEGENDARY_LEVEL + " shiny=" + wildShiny(random);
    }

    private static boolean wildShiny(RandomSource random) {
        return random.nextFloat() < 1 / Math.max(1f, Cobblemon.config.getShinyRate());
    }

    public static boolean analyserShiny(Pokemon pokemon, @Nullable ServerPlayer player, RandomSource random) {
        return shiny(ANALYSER_SHINY_RATE, pokemon, player, random);
    }

    public static boolean skyShiny(Pokemon pokemon, @Nullable ServerPlayer player, RandomSource random) {
        return shiny(SKY_SHINY_RATE, pokemon, player, random);
    }

    private static boolean shiny(float baseRate, Pokemon pokemon, @Nullable ServerPlayer player, RandomSource random) {
        float rate = baseRate;
        if (player != null) {
            ShinyChanceCalculationEvent event = new ShinyChanceCalculationEvent(baseRate, pokemon);
            CobblemonEvents.SHINY_CHANCE_CALCULATION.post(event);
            rate = event.calculate(player);
        }
        return random.nextFloat() < 1 / Math.max(1f, rate);
    }

    public static @Nullable String pick(WormholeType type, int rings, RandomSource random) {
        int difficulty = Math.max(1, type.difficulty());
        List<Candidate> pool = switch (type) {
            case WHITE -> ultraBeasts();
            case GOLD -> withoutMonumentLegends(legendaries());
            default -> regular(difficulty);
        };
        if (pool.isEmpty()) return null;
        Candidate chosen = pool.get(random.nextInt(pool.size()));

        float shinyRate = Math.max(1f, Cobblemon.config.getShinyRate());
        boolean shiny = rings >= WarpTunnel.SHINY_RINGS || random.nextFloat() < (1 + SHINY_BOOST_PER_RING * rings) / shinyRate;
        int level = type == WormholeType.WHITE || type == WormholeType.GOLD ? LEGENDARY_LEVEL : LEVELS[difficulty - 1];
        return chosen.properties() + " level=" + level + " shiny=" + shiny;
    }

    public static float alphaChance(int rings) {
        return ALPHA_BASE * (1 + ALPHA_BOOST_PER_RING * rings);
    }

    public static void atLeastAverageSize(Pokemon pokemon) {
        float min = Cobblemon.config.getPokemonIntrinsicSizeMin();
        float max = Cobblemon.config.getPokemonIntrinsicSizeMax();
        if (max <= min) return;
        float average = min + (max - min) * 2 / PokemonSizeCategory.values().length;
        if (pokemon.getScaleModifier() >= average) return;
        float scale = average + ThreadLocalRandom.current().nextFloat() * (max - average);
        pokemon.setScaleModifier(scale);
        try {
            Method setIntrinsic = Pokemon.class.getDeclaredMethod("setIntrinsicScale", float.class);
            setIntrinsic.setAccessible(true);
            setIntrinsic.invoke(pokemon, scale);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Wormholes.LOGGER.debug("Couldn't set intrinsic scale", e);
        }
    }

    private static final Set<String> MONUMENT_LEGENDS = Set.of(
            "articuno", "zapdos", "moltres", "mew", "celebi", "entei", "raikou", "suicune", "lugia", "hooh",
            "latias", "latios", "regirock", "regice", "registeel", "uxie", "mesprit", "azelf", "dialga", "palkia",
            "giratina", "heatran", "regigigas", "cresselia", "darkrai", "victini", "cobalion", "terrakion",
            "virizion", "reshiram", "zekrom", "kyurem", "keldeo", "meloetta", "enamorus", "hoopa", "xerneas",
            "yveltal", "cosmog", "marshadow", "zacian", "zamazenta", "eternatus", "regieleki", "regidrago",
            "glastrier", "spectrier", "calyrex", "wochien", "chienpao", "tinglu", "chiyu");

    private static final Set<String> CLONED_LEGENDS = Set.of("cosmog", "cosmoem", "solgaleo", "lunala");

    private static List<Candidate> withoutMonumentLegends(List<Candidate> pool) {
        boolean monuments = FabricLoader.getInstance().isModLoaded("legendarymonuments");
        return pool.stream().filter(c -> {
            String species = c.properties().substring(c.properties().indexOf(':') + 1).split(" ")[0];
            return !CLONED_LEGENDS.contains(species) && !(monuments && MONUMENT_LEGENDS.contains(species));
        }).toList();
    }

    private static List<Candidate> ultraBeasts() {
        List<Candidate> pool = new ArrayList<>();
        for (Species s : PokemonSpecies.getSpecies()) {
            if (!s.getImplemented() || EVOLVED_ULTRA_BEASTS.contains(s.getResourceIdentifier().getPath())
                    || !s.getLabels().contains("ultra_beast")) continue;
            pool.add(new Candidate("species=" + s.getResourceIdentifier(), typeNames(s.getTypes())));
        }
        Candidate necrozma = candidate("necrozma");
        if (necrozma != null) pool.add(necrozma);
        return pool;
    }

    private static List<Candidate> legendaries() {
        List<Candidate> found = new ArrayList<>();
        for (String entry : PokemonTags.get(ResourceLocation.fromNamespaceAndPath("hyperspace_wormholes", "legendaries"))) {
            Candidate c = candidate(entry);
            if (c != null) found.add(c);
        }
        return found;
    }

    private static List<Candidate> regular(int difficulty) {
        List<Candidate> found = new ArrayList<>();
        for (String entry : PokemonTags.get(Wormholes.id("wormhole_lv" + difficulty))) {
            Candidate c = candidate(entry);
            if (c != null) found.add(c);
        }
        return found;
    }

    private static @Nullable Candidate candidate(String entry) {
        String[] parts = entry.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return null;
        Species species = find(parts[0]);
        if (species == null || !species.getImplemented()) return null;
        Iterable<ElementalType> types = species.getTypes();
        for (int i = 1; i < parts.length; i++) {
            for (FormData form : species.getForms()) {
                if (form.getAspects().contains(parts[i])) types = form.getTypes();
            }
        }
        StringBuilder properties = new StringBuilder("species=").append(species.getResourceIdentifier());
        for (int i = 1; i < parts.length; i++) properties.append(' ').append(parts[i]);
        return new Candidate(properties.toString(), typeNames(types));
    }

    private static @Nullable Species find(String id) {
        ResourceLocation location = id.contains(":") ? ResourceLocation.tryParse(id)
                : ResourceLocation.fromNamespaceAndPath("cobblemon", id);
        Species s = location == null ? null : PokemonSpecies.getByIdentifier(location);
        return s != null ? s : PokemonSpecies.getByName(id);
    }

    private static List<String> typeNames(Iterable<ElementalType> types) {
        List<String> names = new ArrayList<>();
        for (ElementalType t : types) names.add(t.getName().toLowerCase(Locale.ROOT));
        return names;
    }
}
