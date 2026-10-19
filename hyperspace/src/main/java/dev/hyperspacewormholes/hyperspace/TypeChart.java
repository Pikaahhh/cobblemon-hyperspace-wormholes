package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.api.types.ElementalType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The type chart, for working out how well a quick-battle move hits.
 */
public class TypeChart {
    private static final Map<String, Map<String, Double>> CHART = new HashMap<>();

    static {
        add("normal", "", "rock steel", "ghost");
        add("fire", "grass ice bug steel", "fire water rock dragon", "");
        add("water", "fire ground rock", "water grass dragon", "");
        add("electric", "water flying", "electric grass dragon", "ground");
        add("grass", "water ground rock", "fire grass poison flying bug dragon steel", "");
        add("ice", "grass ground flying dragon", "fire water ice steel", "");
        add("fighting", "normal ice rock dark steel", "poison flying psychic bug fairy", "ghost");
        add("poison", "grass fairy", "poison ground rock ghost", "steel");
        add("ground", "fire electric poison rock steel", "grass bug", "flying");
        add("flying", "grass fighting bug", "electric rock steel", "");
        add("psychic", "fighting poison", "psychic steel", "dark");
        add("bug", "grass psychic dark", "fire fighting poison flying ghost steel fairy", "");
        add("rock", "fire ice flying bug", "fighting ground steel", "");
        add("ghost", "psychic ghost", "dark", "normal");
        add("dragon", "dragon", "steel", "fairy");
        add("dark", "psychic ghost", "fighting dark fairy", "");
        add("steel", "ice rock fairy", "fire water electric steel", "");
        add("fairy", "fighting dragon dark", "fire poison steel", "");
    }

    private static void add(String attacker, String strong, String weak, String immune) {
        Map<String, Double> row = new HashMap<>();
        for (String type : strong.split(" ")) if (!type.isEmpty()) row.put(type, 2.0);
        for (String type : weak.split(" ")) if (!type.isEmpty()) row.put(type, 0.5);
        for (String type : immune.split(" ")) if (!type.isEmpty()) row.put(type, 0.0);
        CHART.put(attacker, row);
    }

    public static double effectiveness(ElementalType move, Iterable<ElementalType> defender) {
        Map<String, Double> row = CHART.getOrDefault(name(move), Map.of());
        double total = 1;
        for (ElementalType type : defender) total *= row.getOrDefault(name(type), 1.0);
        return total;
    }

    private static String name(ElementalType type) {
        return type.getName().toLowerCase(Locale.ROOT);
    }
}
