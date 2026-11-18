package dev.hyperspacewormholes.wormholes.ride;

import dev.hyperspacewormholes.wormholes.GameplayConfig;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.ShinyChanceCalculationEvent;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.PokemonSizeCategory;
import com.cobblemon.mod.common.pokemon.Species;
import dev.hyperspacewormholes.wormholes.WormholeConfig;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.PokemonTags;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Picks the wild Pokemon waiting past each tunnel wormhole from its colour, plus its level and its shiny and alpha odds from its rings.
 * Normal wormholes use the level tags, legendaries and Ultra Beasts have their own pools, and the Ultra Space Wilds use each colour's pool file (WormholePools), some legendaries needing certain legendaries in the party as in the game.
 * Odds follow the wormhole's rings and the light-years flown (WarpTunnel.band); legendaries and Ultra Beasts use the shiny odds from the settings. Necrozma is only ever had by beating it (NecrozmaFight), and species Cobblemon hasn't implemented are skipped.
 */
public final class WormholePokemon {
    public static final List<String> CATEGORIES = List.of("regular", "legendary", "mythical", "ultra_beast", "paradox");
    private static final Set<String> SPECIAL = Set.of("legendary", "mythical", "ultra_beast", "paradox");
    private static final int SHINY_BOOST_PER_RING = 3;
    /** The game's shiny percent for each ring count at the top light-year band (b = 10). */
    private static final double[] GAME_TOP_PERCENT = {1, 10, 19, 36};
    private static final float ALPHA_BASE = 0.02f;
    private static final float ALPHA_BOOST_PER_RING = 1.5f;
    private static final int[] LEVELS = {45, 65, 80};
    private static final int LEGENDARY_LEVEL = 75;
    private static final int GAME_LEVEL = 60;
    private static final float ANALYSER_SHINY_RATE = 16f;
    private static final Set<String> EVOLVED_ULTRA_BEASTS = Set.of("naganadel");
    private static final int WILDS_LEGEND_RINGS = 3;
    private static final double OWNED_LEGEND_WEIGHT = 0.25;
    private static final double VISIT_LIGHT_YEARS = 5_000;
    private static final Set<String> CLONED_LEGENDS = Set.of("cosmog", "cosmoem", "solgaleo", "lunala");
    private static final List<WorldOdds> ULTRA_BEAST_WORLD_ODDS = List.of(
            new WorldOdds("nihilego", 60, 2, 1, 1), new WorldOdds("buzzwole", 18, 3, 1, 1),
            new WorldOdds("pheromosa", 17, 3, 1, 1), new WorldOdds("xurkitree", 2, 71, 20, 5),
            new WorldOdds("kartana", 1, 10, 25, 15), new WorldOdds("celesteela", 1, 10, 25, 15),
            new WorldOdds("guzzlord", 1, 1, 15, 50), new WorldOdds("necrozma", 1, 1, 1, 1),
            new WorldOdds("stakataka", 1, 4, 10, 8), new WorldOdds("blacephalon", 1, 4, 10, 8),
            new WorldOdds("poipole", 1, 3, 6, 4));

    private record Candidate(String properties, List<String> types) {}

    private record WorldOdds(String species, int... byRings) {}

    private WormholePokemon() {}

    public static @Nullable String ultraBeastOfType(String type, RandomSource random) {
        String wanted = type.toLowerCase(Locale.ROOT);
        List<Candidate> pool = ultraBeasts().stream().filter(c -> c.types().contains(wanted)).toList();
        if (pool.isEmpty()) return null;
        Candidate chosen = pool.get(random.nextInt(pool.size()));
        return spawn(chosen, LEGENDARY_LEVEL, wildShiny(chosen, random));
    }

    /** The Item Analyser's regular Pokemon: every Wilds colour's pokemon list together, by their weights and levels,
     *  ones of the gem's type when there are any, else any of them. */
    public static @Nullable String regularOfType(String type, RandomSource random) {
        String wanted = type.toLowerCase(Locale.ROOT);
        List<Pooled> all = new ArrayList<>();
        for (WormholePools.Pool pool : WormholePools.wilds()) all.addAll(pooled(pool.pokemon()));
        List<Pooled> matching = all.stream().filter(p -> p.candidate().types().contains(wanted)).toList();
        List<Pooled> pool = matching.isEmpty() ? all : matching;
        if (pool.isEmpty()) return null;
        Pooled chosen = weighted(pool, null, random);
        return spawn(chosen.candidate(), chosen.entry().level(GAME_LEVEL, random), wildShiny(chosen.candidate(), random));
    }

    public static @Nullable String ultraBeastNamed(String species, RandomSource random) {
        Candidate chosen = candidate(species);
        return chosen == null ? null : spawn(chosen, LEGENDARY_LEVEL, wildShiny(chosen, random));
    }

    private static String spawn(Candidate c, int level, boolean shiny) {
        return c.properties() + " level=" + level + " shiny=" + shiny;
    }

    private static boolean wildShiny(Candidate c, RandomSource random) {
        return random.nextFloat() < 1 / shinyRate(find(speciesOf(c)));
    }

    /** "1 in N" for this species past a wormhole: the settings' Ultra Beast odds for an Ultra Beast, their legendary odds for
     *  a legendary, mythical or paradox Pokemon (0 in either is Cobblemon's own rate), and Cobblemon's rate for the rest. */
    private static float shinyRate(@Nullable Species species) {
        Set<String> labels = species == null ? Set.of() : species.getLabels();
        GameplayConfig config = GameplayConfig.get();
        if (labels.contains("ultra_beast")) return GameplayConfig.specialShinyRate(config.wormholeUltraBeastShinyOneIn);
        if (labels.stream().anyMatch(SPECIAL::contains)) return GameplayConfig.specialShinyRate(config.wormholeLegendaryShinyOneIn);
        return GameplayConfig.specialShinyRate(0);
    }

    public static boolean analyserShiny(Pokemon pokemon, @Nullable ServerPlayer player, RandomSource random) {
        if (pokemon.getSpecies().getLabels().stream().anyMatch(SPECIAL::contains)) return random.nextFloat() < 1 / shinyRate(pokemon.getSpecies());
        return random.nextDouble() < shinyChance(0, 1);
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
            case GOLD -> withoutClonedLegends(legendaries());
            default -> regular(difficulty);
        };
        if (pool.isEmpty()) return null;
        Candidate chosen = pool.get(random.nextInt(pool.size()));
        int level = type == WormholeType.WHITE || type == WormholeType.GOLD ? LEGENDARY_LEVEL : LEVELS[difficulty - 1];
        return spawn(chosen, level, ringShiny(chosen, rings, random));
    }

    public static @Nullable String wilds(ResourceKey<Level> world, WormholeType type, int rings, double lightYears,
                                         RandomSource random, @Nullable ServerPlayer player) {
        WormholePools.Pool pool = WormholePools.of(world);
        boolean game = WormholeConfig.get().gameWormholeColours;
        int band = WarpTunnel.band(lightYears);
        boolean legend = GameplayConfig.get().wormholeLegendaries
                && (game ? random.nextDouble() < legendChance(rings, band) : rings >= WILDS_LEGEND_RINGS);
        List<Pooled> legends = !legend ? List.of()
                : pooled(pool.legendaries()).stream().filter(p -> partyHas(player, p.entry().party())).toList();
        List<Pooled> found = legends.isEmpty() ? pooled(pool.pokemon()) : legends;
        if (found.isEmpty()) return pick(type, rings, random);
        Pooled chosen = weighted(found, !legends.isEmpty() && game ? player : null, random);
        if (game) {
            return spawn(chosen.candidate(), chosen.entry().level(GAME_LEVEL, random),
                    legends.isEmpty() ? random.nextDouble() < shinyChance(rings, band) : wildShiny(chosen.candidate(), random));
        }
        int level = !legends.isEmpty() ? LEGENDARY_LEVEL : LEVELS[Math.max(1, type.difficulty()) - 1];
        return spawn(chosen.candidate(), chosen.entry().level(level, random), ringShiny(chosen.candidate(), rings, random));
    }

    /** A pool entry with its species found; species Cobblemon hasn't implemented, and ones that never come from a
     *  wormhole (Ultra Beasts, which keep their own worlds, Cosmog's line and Necrozma), are left out. */
    private record Pooled(Candidate candidate, WormholePools.Entry entry) {}

    private static List<Pooled> pooled(List<WormholePools.Entry> entries) {
        List<Pooled> found = new ArrayList<>();
        for (WormholePools.Entry entry : entries) {
            Candidate c = candidate(entry.pokemon());
            if (c == null || locked(c)) continue;
            found.add(new Pooled(c, entry));
        }
        return found;
    }

    private static boolean locked(Candidate c) {
        String species = speciesOf(c);
        if (CLONED_LEGENDS.contains(species) || species.equals("necrozma")) return true;
        Species s = find(species);
        return s != null && s.getLabels().contains("ultra_beast");
    }

    /** By the entries' weights; with a player, a legendary they already own counts OWNED_LEGEND_WEIGHT as much (unless they own them all). */
    private static Pooled weighted(List<Pooled> pool, @Nullable ServerPlayer player, RandomSource random) {
        double[] weights = new double[pool.size()];
        double total = 0;
        boolean ownsAll = player != null && pool.stream().allMatch(p -> owns(player, p.candidate()));
        for (int i = 0; i < weights.length; i++) {
            weights[i] = pool.get(i).entry().weight() * (player != null && !ownsAll && owns(player, pool.get(i).candidate()) ? OWNED_LEGEND_WEIGHT : 1);
            total += weights[i];
        }
        double roll = random.nextDouble() * total;
        int i = 0;
        while (i < weights.length - 1 && roll >= weights[i]) roll -= weights[i++];
        return pool.get(i);
    }

    public static boolean known(String species) {
        return candidate(species) != null;
    }

    public static List<String> speciesNames() {
        return PokemonSpecies.getImplemented().stream().map(s -> s.getResourceIdentifier().getPath()).sorted().toList();
    }

    public static List<String> categories() {
        List<String> all = new ArrayList<>(CATEGORIES);
        all.addAll(WormholeDestinations.wildsNames());
        return all;
    }

    public static @Nullable String chosen(ResourceKey<Level> world, @Nullable String category, @Nullable String species,
                                          int rings, RandomSource random, @Nullable ServerPlayer player) {
        ResourceKey<Level> poolOf = species == null ? WormholeDestinations.named(category) : null;
        if (poolOf != null && WormholeDestinations.isWilds(poolOf)) {
            return wilds(poolOf, WormholeDestinations.colourOf(poolOf), rings, VISIT_LIGHT_YEARS, random, player);
        }
        Candidate chosen = species != null ? candidate(species) : null;
        if (species == null && category != null) {
            List<Candidate> here = new ArrayList<>();
            if (WormholeDestinations.isWilds(world)) {
                WormholePools.Pool pool = WormholePools.of(world);
                for (Pooled p : pooled(pool.pokemon())) here.add(p.candidate());
                for (Pooled p : pooled(pool.legendaries())) here.add(p.candidate());
            }
            String beast = UltraBeastWorlds.beastOf(world);
            Candidate own = beast == null ? null : candidate(beast);
            if (own != null) here.add(own);
            List<Candidate> fits = here.stream().filter(c -> is(c, category)).toList();
            if (fits.isEmpty()) {
                fits = new ArrayList<>();
                for (Species s : PokemonSpecies.getImplemented()) {
                    Candidate c = candidate(s.getResourceIdentifier().toString());
                    if (c != null && is(c, category)) fits.add(c);
                }
            }
            if (!fits.isEmpty()) chosen = fits.get(random.nextInt(fits.size()));
        }
        if (chosen == null) return null;
        return spawn(chosen, GAME_LEVEL, is(chosen, "regular")
                ? random.nextDouble() < shinyChance(rings, WarpTunnel.band(VISIT_LIGHT_YEARS)) : wildShiny(chosen, random));
    }

    private static boolean is(Candidate c, String category) {
        Species s = find(speciesOf(c));
        Set<String> labels = s == null ? Set.of() : s.getLabels();
        return category.equals("regular") ? labels.stream().noneMatch(SPECIAL::contains) : labels.contains(category);
    }

    public static @Nullable String ultraBeastWorld(int rings, RandomSource random) {
        int tier = Math.min(WarpTunnel.GAME_TOP_RINGS, rings);
        List<Candidate> found = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (WorldOdds odds : ULTRA_BEAST_WORLD_ODDS) {
            Candidate c = candidate(odds.species());
            if (c == null) continue;
            found.add(c);
            weights.add(odds.byRings()[tier]);
            total += odds.byRings()[tier];
        }
        if (total == 0) return null;
        int roll = random.nextInt(total), i = 0;
        while (roll >= weights.get(i)) roll -= weights.get(i++);
        return spawn(found.get(i), GAME_LEVEL, wildShiny(found.get(i), random));
    }

    private static double legendChance(int rings, int band) {
        return switch (rings) {
            case 0 -> 0.03;
            case 1 -> (band + 2) / 100.0;
            case 2 -> (3 * band + 4) / 100.0;
            default -> 1;
        };
    }

    /**
     * The game's chance for this many rings and light-year band, with the settings' four "1 in N" values standing for
     * the no-ring chance and each ring count's chance at the top band: a shorter ride gets the same share of the way
     * from the no-ring chance to its ring count's as the game's own numbers do (b% with one ring, 2b - 1% with two,
     * 4b - 4% with three, never under the no-ring chance), so the defaults roll as the game's.
     */
    private static double shinyChance(int rings, int band) {
        GameplayConfig.ShinyOdds odds = GameplayConfig.get().wormholeShinyOneIn;
        int tier = Math.min(rings, GAME_TOP_PERCENT.length - 1);
        double none = odds.chance(0);
        if (tier == 0) return none;
        double percent = switch (tier) {
            case 1 -> band;
            case 2 -> 2 * band - 1;
            default -> 4 * band - 4;
        };
        double share = (Math.max(1, percent) - 1) / (GAME_TOP_PERCENT[tier] - 1);
        return Math.max(0, Math.min(1, none + (odds.chance(tier) - none) * share));
    }

    private static boolean owns(@Nullable ServerPlayer player, Candidate c) {
        Species species = player == null ? null : find(speciesOf(c));
        return species != null && Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player)
                .getHighestKnowledgeForSpecies(species.getResourceIdentifier()) == PokedexEntryProgress.OWNED;
    }

    private static boolean ringShiny(Candidate c, int rings, RandomSource random) {
        float shinyRate = shinyRate(find(speciesOf(c)));
        double base = GameplayConfig.get().wormholeShinyOneIn.chance(0) * 100;
        return rings >= WarpTunnel.SHINY_RINGS || random.nextFloat() < (base + SHINY_BOOST_PER_RING * rings) / shinyRate;
    }

    private static boolean partyHas(@Nullable ServerPlayer player, List<String> species) {
        if (species.isEmpty()) return true;
        if (player == null) return false;
        Set<String> party = new HashSet<>();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            party.add(pokemon.getSpecies().getResourceIdentifier().getPath());
        }
        return party.containsAll(species);
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

    private static List<Candidate> withoutClonedLegends(List<Candidate> pool) {
        return pool.stream().filter(c -> !CLONED_LEGENDS.contains(speciesOf(c))).toList();
    }

    private static String speciesOf(Candidate c) {
        return c.properties().substring(c.properties().indexOf(':') + 1).split(" ")[0];
    }

    private static List<Candidate> ultraBeasts() {
        List<Candidate> pool = new ArrayList<>();
        for (Species s : PokemonSpecies.getSpecies()) {
            if (!s.getImplemented() || EVOLVED_ULTRA_BEASTS.contains(s.getResourceIdentifier().getPath())
                    || !s.getLabels().contains("ultra_beast")) continue;
            pool.add(new Candidate("species=" + s.getResourceIdentifier(), typeNames(s.getTypes())));
        }
        return pool;
    }

    private static List<Candidate> legendaries() {
        return tagged(Wormholes.id("legendaries"));
    }

    private static List<Candidate> regular(int difficulty) {
        return tagged(Wormholes.id("wormhole_lv" + difficulty));
    }

    private static List<Candidate> tagged(ResourceLocation tag) {
        List<Candidate> found = new ArrayList<>();
        for (String entry : PokemonTags.get(tag)) {
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
