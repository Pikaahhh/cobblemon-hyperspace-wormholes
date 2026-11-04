package dev.hyperspacewormholes.wormholes.boss;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One boss's data, read by BossDefs from data/&lt;namespace&gt;/bosses/&lt;id&gt;.json with config overrides on top.
 * Every key is optional: name, properties, moves, HP scaling by trainer count, size, battle AI level, boosts, music, ultimate, forms (changes at HP thresholds), variants, the defeat scene timings and banned_moves.
 * A Form or Variant leaves out what it doesn't change.
 */
public final class BossDef {
    public static final int MAX_MOVES = 6;
    public static final List<String> STATS = List.of("atk", "def", "spa", "spd", "spe", "accuracy", "evasion");
    public static final List<String> BANNED = List.of("fissure", "sheercold", "guillotine", "horndrill", "endeavor", "painsplit",
            "finalgambit", "superfang", "naturesmadness", "ruination", "guardianofalola", "destinybond", "perishsong");

    /** hardAgainBelow: in hard mode the move is used a second time under that HP fraction (0: never). */
    public record Ultimate(String move, boolean zMove, double hpBelow, double hardAgainBelow) {}

    public record DefeatEffect(String id, int at, List<String> aspects) {}

    public record Form(double hpBelow, @Nullable String name, String properties, @Nullable String species, int level,
                       @Nullable String ability, @Nullable String item, @Nullable ResourceLocation heldItem, List<String> moves,
                       Map<String, Integer> boosts, @Nullable ResourceLocation music, @Nullable Ultimate ultimate,
                       @Nullable String zItem) {}

    public record Variant(@Nullable String name, String properties, List<String> moves, @Nullable ResourceLocation music) {}

    public final ResourceLocation id;
    public final @Nullable String name;
    public final String properties;
    public final List<String> moves;
    public final double hpPerTrainer;
    public final double @Nullable [] scaling;
    public final int maxTrainers;
    public final float scale;
    public final int aiSkill;
    public final Map<String, Integer> boosts;
    public final @Nullable ResourceLocation music;
    public final @Nullable Ultimate ultimate;
    public final List<Form> forms;
    public final Map<String, Variant> variants;
    /** For a boss whose code names its species itself (spawn_species): what /boss spawn makes it from. */
    public final String spawnSpecies;
    public final @Nullable String defeatedProperties;
    public final int powerUpTicks, defeatTicks, defeatFormTicks;
    public final @Nullable DefeatEffect defeatEffect;
    public final double openingSeconds;
    public final List<String> bannedMoves;

    private BossDef(ResourceLocation id, JsonObject json) {
        this.id = id;
        this.name = string(json, "name");
        this.properties = string(json, "properties", "");
        this.moves = moves(json);
        this.hpPerTrainer = number(json, "hp_per_trainer", 0);
        if (json.has("scaling") && json.get("scaling").isJsonArray() && !json.getAsJsonArray("scaling").isEmpty()) {
            JsonArray array = json.getAsJsonArray("scaling");
            scaling = new double[array.size()];
            for (int i = 0; i < array.size(); i++) scaling[i] = array.get(i).getAsDouble();
        } else {
            scaling = null;
        }
        this.maxTrainers = integer(json, "max_trainers", 0);
        this.scale = json.has("scale") ? json.get("scale").getAsFloat() : 0;
        this.aiSkill = integer(json, "ai_skill", -1);
        this.boosts = boosts(json);
        this.music = id(json, "music");
        this.ultimate = ultimate(json);
        List<Form> forms = new ArrayList<>();
        if (json.has("forms")) {
            for (JsonElement element : json.getAsJsonArray("forms")) {
                JsonObject form = element.getAsJsonObject();
                forms.add(new Form(number(form, "hp_below", 0.5), string(form, "name"),
                        string(form, "properties", ""), string(form, "species"), integer(form, "level", 0),
                        string(form, "ability"), string(form, "item"), id(form, "held_item"), moves(form), boosts(form),
                        id(form, "music"), ultimate(form), string(form, "z_item")));
            }
        }
        forms.sort((a, b) -> Double.compare(b.hpBelow(), a.hpBelow()));
        this.forms = List.copyOf(forms);
        Map<String, Variant> variants = new LinkedHashMap<>();
        if (json.has("variants")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("variants").entrySet()) {
                JsonObject variant = entry.getValue().getAsJsonObject();
                variants.put(entry.getKey(), new Variant(string(variant, "name"), string(variant, "properties", ""), moves(variant),
                        id(variant, "music")));
            }
        }
        this.variants = Map.copyOf(variants);
        this.spawnSpecies = string(json, "spawn_species", "");
        this.defeatedProperties = string(json, "defeated_properties");
        this.powerUpTicks = integer(json, "power_up_ticks", 80);
        this.defeatTicks = integer(json, "defeat_ticks", 240);
        this.defeatFormTicks = integer(json, "defeat_form_ticks", defeatTicks);
        if (json.has("defeat_effect") && json.get("defeat_effect").isJsonObject()) {
            JsonObject effect = json.getAsJsonObject("defeat_effect");
            List<String> aspects = new ArrayList<>();
            if (effect.has("aspects")) for (JsonElement aspect : effect.getAsJsonArray("aspects")) aspects.add(aspect.getAsString());
            this.defeatEffect = new DefeatEffect(string(effect, "id", ""), integer(effect, "at", 0), List.copyOf(aspects));
        } else {
            this.defeatEffect = null;
        }
        this.openingSeconds = number(json, "opening_transition_seconds", 0);
        if (json.has("banned_moves") && json.get("banned_moves").isJsonArray()) {
            List<String> banned = new ArrayList<>();
            for (JsonElement move : json.getAsJsonArray("banned_moves")) banned.add(toId(move.getAsString()));
            this.bannedMoves = List.copyOf(banned);
        } else {
            this.bannedMoves = BANNED;
        }
    }

    public static BossDef parse(ResourceLocation id, JsonObject json) {
        return new BossDef(id, json);
    }

    public static BossDef empty(ResourceLocation id) {
        return new BossDef(id, new JsonObject());
    }

    public @Nullable Variant variant(@Nullable String name) {
        return name == null ? null : variants.get(name);
    }

    private static @Nullable String string(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : null;
    }

    private static String string(JsonObject json, String key, String fallback) {
        String value = string(json, key);
        return value == null ? fallback : value;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static double number(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    private static String toId(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static @Nullable ResourceLocation id(JsonObject json, String key) {
        String value = string(json, key);
        return value == null || value.isBlank() ? null : ResourceLocation.parse(value);
    }

    private static List<String> moves(JsonObject json) {
        List<String> out = new ArrayList<>();
        if (!json.has("moves")) return out;
        for (JsonElement move : json.getAsJsonArray("moves")) {
            String id = toId(move.getAsString());
            if (!id.isEmpty() && !out.contains(id) && out.size() < MAX_MOVES) out.add(id);
        }
        return List.copyOf(out);
    }

    private static Map<String, Integer> boosts(JsonObject json) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (!json.has("boosts")) return out;
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("boosts").entrySet()) {
            String stat = entry.getKey().toLowerCase(Locale.ROOT);
            // "highest": the highest base stat of the form it has then (Boss.powerUp)
            if ((STATS.contains(stat) || stat.equals("highest")) && entry.getValue().getAsInt() != 0) out.put(stat, entry.getValue().getAsInt());
        }
        return Map.copyOf(out);
    }

    private static @Nullable Ultimate ultimate(JsonObject json) {
        if (!json.has("ultimate") || !json.get("ultimate").isJsonObject()) return null;
        JsonObject ultimate = json.getAsJsonObject("ultimate");
        String move = string(ultimate, "move");
        if (move == null) return null;
        return new Ultimate(toId(move), ultimate.has("zmove") && ultimate.get("zmove").getAsBoolean(), number(ultimate, "hp_below", 0.33),
                number(ultimate, "hard_again_below", 0));
    }
}
