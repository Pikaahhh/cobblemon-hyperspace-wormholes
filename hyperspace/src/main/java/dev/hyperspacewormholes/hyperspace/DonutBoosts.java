package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.CobblemonItemComponents;
import com.cobblemon.mod.common.api.berry.Berries;
import com.cobblemon.mod.common.api.cooking.Flavour;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.item.components.IngredientComponent;
import com.cobblemon.mod.common.pokemon.Species;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The donut maths, Legends Z-A style: berry flavour points set the stars (120/240/350/700/960 total), the stars
 * multiply the calories, and the calories buy trip time at the drain rate for those stars. The strongest flavour picks
 * the kind of donut (Meringue, Curry, Jam, Chocolate, Cream, or Rainbow on a tie). At a spatial distortion its rank
 * sets the drain rate, and a donut with fewer stars burns 1.5x (one under) or 2.5x (two or more under) as fast.
 */
public class DonutBoosts {
    public static final ResourceLocation DONUT_SEASONINGS = Hyperspace.id("donut_seasonings");
    public static final ResourceLocation DONUT_BERRIES = Hyperspace.id("donut_berries");

    public static final Map<String, String> BERRY_TYPES = Map.ofEntries(
            Map.entry("cobblemon:occa_berry", "fire"), Map.entry("cobblemon:passho_berry", "water"),
            Map.entry("cobblemon:wacan_berry", "electric"), Map.entry("cobblemon:rindo_berry", "grass"),
            Map.entry("cobblemon:yache_berry", "ice"), Map.entry("cobblemon:chople_berry", "fighting"),
            Map.entry("cobblemon:kebia_berry", "poison"), Map.entry("cobblemon:shuca_berry", "ground"),
            Map.entry("cobblemon:coba_berry", "flying"), Map.entry("cobblemon:payapa_berry", "psychic"),
            Map.entry("cobblemon:tanga_berry", "bug"), Map.entry("cobblemon:charti_berry", "rock"),
            Map.entry("cobblemon:kasib_berry", "ghost"), Map.entry("cobblemon:haban_berry", "dragon"),
            Map.entry("cobblemon:colbur_berry", "dark"), Map.entry("cobblemon:babiri_berry", "steel"),
            Map.entry("cobblemon:chilan_berry", "normal"), Map.entry("cobblemon:roseli_berry", "fairy"));

    public static final List<String> FLAVOURS = List.of("Sweet", "Spicy", "Sour", "Bitter", "Dry");
    private static final List<Flavour> COBBLEMON_FLAVOURS = List.of(Flavour.SWEET, Flavour.SPICY, Flavour.SOUR, Flavour.BITTER, Flavour.DRY);
    private static final int HYPER_STRENGTH = 4;
    public static final List<String> KINDS = List.of("meringue", "curry", "jam", "chocolate", "cream");
    public static final String RAINBOW = "rainbow";

    public record Berry(int calories) {
    }

    public static final Map<String, Berry> BERRIES = new HashMap<>();
    public static final Map<String, String> HYPER = new HashMap<>();

    static {
        berry("cheri", 60);
        berry("chesto", 60);
        berry("pecha", 60);
        berry("rawst", 60);
        berry("aspear", 60);
        berry("oran", 60);
        berry("persim", 60);
        berry("lum", 65);
        berry("sitrus", 65);
        berry("pomeg", 65);
        berry("kelpsy", 65);
        berry("qualot", 65);
        berry("hondew", 65);
        berry("grepa", 65);
        berry("tamato", 65);
        berry("occa", 70);
        berry("passho", 70);
        berry("wacan", 70);
        berry("rindo", 70);
        berry("yache", 70);
        berry("chople", 70);
        berry("kebia", 70);
        berry("shuca", 70);
        berry("coba", 70);
        berry("payapa", 70);
        berry("tanga", 70);
        berry("charti", 70);
        berry("kasib", 70);
        berry("haban", 70);
        berry("colbur", 70);
        berry("babiri", 70);
        berry("chilan", 70);
        berry("roseli", 70);
        hyper("occa", 180);
        hyper("passho", 200);
        hyper("wacan", 160);
        hyper("rindo", 210);
        hyper("yache", 250);
        hyper("chople", 250);
        hyper("kebia", 270);
        hyper("shuca", 230);
        hyper("coba", 240);
        hyper("payapa", 300);
        hyper("tanga", 300);
        hyper("charti", 330);
        hyper("kasib", 270);
        hyper("haban", 370);
        hyper("colbur", 370);
        hyper("babiri", 400);
        hyper("chilan", 370);
        hyper("roseli", 340);
    }

    private static void berry(String name, int calories) {
        BERRIES.put("cobblemon:" + name + "_berry", new Berry(calories));
    }

    private static void hyper(String name, int calories) {
        String id = "hyperspace_wormholes:hyperspace_" + name + "_berry";
        BERRIES.put(id, new Berry(calories));
        HYPER.put(id, "cobblemon:" + name + "_berry");
    }

    public static final Map<String, Integer> RARITY = Map.of(
            "minecraft:glistering_melon_slice", 1,
            "minecraft:golden_carrot", 1,
            "minecraft:golden_apple", 1,
            "minecraft:enchanted_golden_apple", 2);

    public static final int[] STAR_SCORES = {120, 240, 350, 700, 960};
    private static final float[] DRAIN = {1f, 1f, 1.6f, 3.5f, 7.5f, 10f};
    private static final float ONE_UNDER_DRAIN = 1.5f;
    private static final float FAR_UNDER_DRAIN = 2.5f;
    private static final int[] SPARKLING_SCORES = {420, 525, 700};
    private static final int[] ALPHA_SCORES = {120, 200, 300};
    private static final int[] ALPHA_POWER = {1, 2, 4, 10};

    public static final List<String> TYPES = List.of("normal", "fire", "water", "grass", "electric", "ice", "fighting",
            "poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy");

    public static final String APPLE = "minecraft:enchanted_golden_apple";
    public static final String RANDOM = "random";
    public static final String ALL_TYPES = "all";
    public static final String GRID_BERRIES = "berries";
    public static final Set<String> MINT_LEAVES = Set.of("cobblemon:red_mint_leaf", "cobblemon:blue_mint_leaf",
            "cobblemon:cyan_mint_leaf", "cobblemon:pink_mint_leaf", "cobblemon:green_mint_leaf", "cobblemon:white_mint_leaf");

    public static final float ALPHA_CHANCE = 0.05f;
    public static final float MINT_ALPHA_CHANCE = 0.05f;
    public static final float LEGENDARY_CHANCE = 0.05f;
    public static final int LEGENDARY_STARS = 5;
    public static final float MEGA_CHANCE = 0.10f;
    public static final int MEGA_STARS = 4;

    public static final int BASE_ENERGY = 90;
    public static final int APPLE_SHINY_ROLLS = 20;

    private static final int WILD_BASE = 100;
    private static final int[][] STAR_BONUS = {{1, 20}, {1, 20}, {21, 35}, {36, 50}, {51, 65}, {66, 85}};
    private static final int[][] CAUGHT_LEVELS = {{13, 19}, {13, 19}, {15, 25}, {20, 35}, {35, 43}, {50, 56}};

    public static List<String> ids(ItemStack stack) {
        List<String> ids = new ArrayList<>();
        ListTag grid = custom(stack).getList(GRID_BERRIES, Tag.TAG_STRING);
        for (int i = 0; i < grid.size(); i++) ids.add(grid.getString(i));
        IngredientComponent ingredients = stack.get(CobblemonItemComponents.INGREDIENT);
        if (ingredients != null) {
            for (ResourceLocation key : ingredients.getIngredientIds()) ids.add(key.toString());
        }
        return ids;
    }

    public static boolean isBerry(String id) {
        return BERRIES.containsKey(id);
    }

    public static String baseBerry(String id) {
        String base = HYPER.getOrDefault(id, id);
        return BERRY_TYPES.containsKey(base) ? base : null;
    }

    public static ItemStack commandDonut(int stars, int seconds, String type, String legendary, String mega, int shiny, int alpha) {
        CompoundTag boosts = new CompoundTag();
        boosts.putInt("stars", stars);
        boosts.putInt("calories", seconds);
        if (type != null) boosts.putString("type", type);
        if (legendary != null) boosts.putString("legendary", legendary);
        if (mega != null) boosts.putString("mega", mega);
        if (shiny > 1) boosts.putInt("shiny", shiny);
        if (alpha > 1) boosts.putInt("alpha", alpha);
        return save(new ItemStack(ModItems.DONUT), boosts);
    }

    private static CompoundTag custom(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag().getCompound(Hyperspace.MOD_ID);
    }

    private static ItemStack save(ItemStack stack, CompoundTag boosts) {
        CompoundTag tag = new CompoundTag();
        tag.put(Hyperspace.MOD_ID, boosts);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static void addGridBerries(ItemStack stack, List<String> berries) {
        if (berries.isEmpty()) return;
        CompoundTag boosts = custom(stack);
        ListTag list = new ListTag();
        for (String id : berries) list.add(StringTag.valueOf(id));
        boosts.put(GRID_BERRIES, list);
        save(stack, boosts);
    }

    public static int[] flavours(List<String> ids) {
        int[] total = new int[FLAVOURS.size()];
        for (String id : ids) {
            int[] values = berryFlavours(id);
            for (int i = 0; i < total.length; i++) total[i] += values[i];
        }
        return total;
    }

    /** One berry's flavours: Cobblemon's numbers, made stronger for a Hyperspace berry. */
    /** What a berry gives a donut, for the list Cobblemon shows while Shift is held. */
    public static List<Component> berryInfo(String id) {
        Berry berry = BERRIES.get(id);
        if (berry == null) return null;
        int[] values = berryFlavours(id);
        int score = 0;
        for (int value : values) score += value;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip.hyperspace_wormholes.berry.header").withStyle(ChatFormatting.BLUE));
        lines.add(Component.translatable("tooltip.hyperspace_wormholes.berry.calories", berry.calories()).withStyle(ChatFormatting.GRAY));
        if (score > 0) lines.add(Component.translatable("tooltip.hyperspace_wormholes.berry.score", score).withStyle(ChatFormatting.GRAY));
        if (values[0] > 0) lines.add(Component.translatable("tooltip.hyperspace_wormholes.berry.sweet").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (values[2] > 0) lines.add(Component.translatable("tooltip.hyperspace_wormholes.berry.sour").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (values[4] > 0) lines.add(Component.translatable("tooltip.hyperspace_wormholes.berry.dry").withStyle(ChatFormatting.LIGHT_PURPLE));
        return lines;
    }

    public static int[] berryFlavours(String id) {
        int[] values = new int[FLAVOURS.size()];
        if (!BERRIES.containsKey(id)) return values;
        var berry = Berries.INSTANCE.getByIdentifier(ResourceLocation.parse(HYPER.getOrDefault(id, id)));
        if (berry == null) return values;
        int strength = HYPER.containsKey(id) ? HYPER_STRENGTH : 1;
        for (int i = 0; i < values.length; i++) values[i] = berry.flavor(COBBLEMON_FLAVOURS.get(i)) * strength;
        return values;
    }

    public static int score(List<String> ids) {
        int score = 0;
        for (int value : flavours(ids)) score += value;
        return score;
    }

    public static int starsForScore(int score) {
        int stars = 0;
        for (int needed : STAR_SCORES) if (score >= needed) stars++;
        return stars;
    }

    public static String kind(List<String> ids) {
        int[] total = flavours(ids);
        int best = 0, top = -1;
        boolean tie = false;
        for (int i = 0; i < total.length; i++) {
            if (total[i] > best) {
                best = total[i];
                top = i;
                tie = false;
            } else if (total[i] == best && best > 0) {
                tie = true;
            }
        }
        if (top < 0) return null;
        return tie ? RAINBOW : KINDS.get(top);
    }

    public static int sparklingPower(List<String> ids) {
        int sweet = flavours(ids)[0], level = 0;
        for (int needed : SPARKLING_SCORES) if (sweet >= needed) level++;
        return level;
    }

    public static int alphaPower(List<String> ids) {
        if (sparklingPower(ids) > 0) return 0;
        int[] total = flavours(ids);
        for (int value : total) if (value > total[0]) return 0;
        int level = 0;
        for (int needed : ALPHA_SCORES) if (total[0] >= needed) level++;
        return level;
    }

    public static int stars(ItemStack stack) {
        CompoundTag custom = custom(stack);
        int fromBerries = stars(ids(stack));
        return custom.contains("stars") ? Math.min(5, Math.max(custom.getInt("stars"), fromBerries)) : fromBerries;
    }

    public static int stars(List<String> ids) {
        return Math.min(starsForScore(score(ids)), 5);
    }

    private static int rawCalories(List<String> ids) {
        int calories = 0;
        for (String id : ids) {
            Berry berry = BERRIES.get(id);
            if (berry != null) calories += berry.calories();
        }
        return calories;
    }

    public static int calories(List<String> ids, int stars) {
        return Math.round(rawCalories(ids) * (1 + 0.1f * stars));
    }

    public static int calories(ItemStack stack) {
        CompoundTag custom = custom(stack);
        int calories = calories(ids(stack), stars(stack));
        return custom.contains("calories") ? custom.getInt("calories") + calories : calories;
    }

    private static int drained(int calories, int stars) {
        return Math.round(calories / DRAIN[Math.min(stars, 5)]);
    }

    public static int seconds(ItemStack stack) {
        CompoundTag custom = custom(stack);
        if (!custom.contains("calories")) return seconds(ids(stack));
        List<String> ids = ids(stack);
        int stars = stars(stack);
        return custom.getInt("calories") + drained(calories(ids, stars), stars);
    }

    public static int seconds(List<String> ids) {
        int stars = stars(ids);
        return Math.max(BASE_ENERGY, drained(calories(ids, stars), stars));
    }

    public static int seconds(ItemStack stack, int rank) {
        int under = rank - stars(stack);
        float drain = DRAIN[Math.min(rank, 5)] * (under >= 2 ? FAR_UNDER_DRAIN : under == 1 ? ONE_UNDER_DRAIN : 1f);
        return Math.max(BASE_ENERGY, Math.round(calories(stack) / drain));
    }

    public static String legendary(ItemStack stack) {
        CompoundTag custom = custom(stack);
        if (!custom.contains("legendary")) return null;
        if (custom.contains("legendary", Tag.TAG_STRING)) return custom.getString("legendary");
        return custom.getBoolean("legendary") ? RANDOM : null;
    }

    public static float legendaryShiny(ItemStack stack) {
        return custom(stack).getFloat("legendShiny");
    }

    public static float legendaryAlpha(ItemStack stack) {
        return custom(stack).getFloat("legendAlpha");
    }

    public static String mega(ItemStack stack) {
        CompoundTag custom = custom(stack);
        return custom.contains("mega") ? custom.getString("mega") : null;
    }

    public static List<String> types(ItemStack stack) {
        CompoundTag custom = custom(stack);
        String fixed = custom.getString("type");
        if (fixed.equals(ALL_TYPES)) return new ArrayList<>(TYPES);
        List<String> types = new ArrayList<>();
        if (TYPES.contains(fixed)) types.add(fixed);
        return types;
    }

    public static int rarity(ItemStack stack) {
        return rarity(ids(stack));
    }

    public static int rarity(List<String> ids) {
        return ids.stream().mapToInt(id -> RARITY.getOrDefault(id, 0)).sum();
    }

    static MutableComponent typeName(String name) {
        ElementalType type = ElementalTypes.get(name);
        return type == null ? Component.literal(name) : type.getDisplayName().copy().withColor(type.getHue());
    }

    private static String flavourText(int[] values) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < values.length; i++) if (values[i] > 0) parts.add(FLAVOURS.get(i) + " " + values[i]);
        return String.join(", ", parts);
    }

    public static String starText(int stars) {
        return "★".repeat(stars);
    }

    public static int minLevel(int stars) {
        return WILD_BASE + STAR_BONUS[stars][0];
    }

    public static int maxLevel(int stars) {
        return WILD_BASE + STAR_BONUS[stars][1];
    }

    public static int minCaughtLevel(int stars) {
        return CAUGHT_LEVELS[stars][0];
    }

    public static int maxCaughtLevel(int stars) {
        return CAUGHT_LEVELS[stars][1];
    }

    public static void addLore(ItemStack stack, List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.hyperspace_wormholes.ring.calories", calories(stack)).withStyle(ChatFormatting.GRAY));
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT || !Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.hyperspace_wormholes.ring.more").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        List<String> ids = ids(stack);
        int score = score(ids);
        if (score > 0) {
            tooltip.add(Component.translatable("tooltip.hyperspace_wormholes.donut.flavor", score, flavourText(flavours(ids))).withStyle(ChatFormatting.GRAY));
        }
        List<String> powers = new ArrayList<>();
        if (sparklingPower(ids) > 0) powers.add("Sparkling Power Lv. " + sparklingPower(ids));
        if (alphaPower(ids) > 0) powers.add("Alpha Power Lv. " + alphaPower(ids));
        DonutPowers.of(ids).describe(powers);
        if (!powers.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.hyperspace_wormholes.donut.powers").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.join(", ", powers)).withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
        int stars = Math.max(1, stars(stack));
        tooltip.add(Component.translatable("tooltip.hyperspace_wormholes.ring.levels", minLevel(stars), maxLevel(stars)).withStyle(ChatFormatting.GRAY));
        List<String> modifiers = new ArrayList<>();
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.US);
        int shiny = shinyRolls(stack);
        if (shiny > 1) modifiers.add(format.format(shiny) + "x Shiny Chance");
        int alpha = Math.round(alphaChance(stack) / ALPHA_CHANCE);
        if (alpha > 1) modifiers.add(format.format(alpha) + "x Alpha Chance");
        odds(modifiers, legendaryShiny(stack), "Shiny Legendary");
        odds(modifiers, legendaryAlpha(stack), "Alpha Legendary");
        String legendary = legendary(stack);
        if (legendary != null) {
            Species species = RANDOM.equals(legendary) ? null : PokemonSpecies.getByName(legendary);
            modifiers.add("Guaranteed " + (species == null ? "Legendary" : species.getTranslatedName().getString()));
        }
        String mega = mega(stack);
        if (mega != null) {
            List<Spawns.MegaForm> forms = RANDOM.equals(mega) ? List.of() : Spawns.megaForms(mega);
            String name = "Mega";
            if (!forms.isEmpty()) {
                name += " " + forms.get(0).species().getTranslatedName().getString();
                if (forms.size() == 1 && !forms.get(0).aspect().equals("mega")) name += " " + forms.get(0).aspect().substring(5).toUpperCase(Locale.ROOT);
            }
            modifiers.add("Guaranteed " + name);
        }
        if (!modifiers.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.hyperspace_wormholes.ring.modifiers").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.join(", ", modifiers)).withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
    }

    private static void odds(List<String> modifiers, float chance, String what) {
        if (chance >= 1) modifiers.add(what);
        else if (chance > 0) modifiers.add(Math.round(chance * 100) + "% " + what);
    }

    public static int shinyRolls(int rarity) {
        return 1 + 3 * rarity;
    }

    public static float alphaChance(ItemStack stack) {
        List<String> ids = ids(stack);
        CompoundTag custom = custom(stack);
        int alpha = custom.contains("alpha") ? custom.getInt("alpha") : 1;
        float boosted = ALPHA_CHANCE * alpha * ALPHA_POWER[alphaPower(ids)];
        return Math.min(1f, boosted + MINT_ALPHA_CHANCE * mints(ids));
    }

    private static int mints(List<String> ids) {
        return (int) ids.stream().filter(MINT_LEAVES::contains).count();
    }

    /**
     * The chance that a portal holds a legendary: a flat {@link #LEGENDARY_CHANCE} on a 5 star trip, else none. Rolled
     * once when the portal opens and shared by everyone who enters; {@code legendary=} on /hyperspace portal forces
     * one.
     */
    public static float legendaryChance(int stars) {
        return stars >= LEGENDARY_STARS ? LEGENDARY_CHANCE : 0f;
    }

    public static float legendaryChance(ItemStack stack, int stars) {
        return legendaryChance(stars);
    }

    /**
     * The chance that a trip gets a Mega boss: a flat {@link #MEGA_CHANCE} on exactly 4 stars, else none. Rolled once
     * per player on entry; {@code mega=} on /hyperspace portal forces one.
     */
    public static float megaChance(int stars) {
        return stars == MEGA_STARS ? MEGA_CHANCE : 0f;
    }

    public static int shinyRolls(ItemStack stack) {
        CompoundTag custom = custom(stack);
        List<String> ids = ids(stack);
        int base = custom.contains("shiny") ? custom.getInt("shiny") : 1;
        return base + 3 * rarity(ids) + APPLE_SHINY_ROLLS * Collections.frequency(ids, APPLE) + sparklingPower(ids);
    }
}
