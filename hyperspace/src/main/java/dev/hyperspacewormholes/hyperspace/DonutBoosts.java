package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.CobblemonItemComponents;
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
 * All the Hoopa Ring and donut maths: reading the berries and seasonings in a donut and turning them into a ring's
 * stars, time, types, legendary or Mega target, shiny and alpha odds and wild levels. Also builds the ring's tooltip.
 */
public class DonutBoosts {
    public static final ResourceLocation DONUT_SEASONINGS = ResourceLocation.fromNamespaceAndPath("hyperspace_wormholes", "donut_seasonings");
    public static final ResourceLocation DONUT_BERRIES = ResourceLocation.fromNamespaceAndPath("hyperspace_wormholes", "donut_berries");

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

    public static final Map<String, Integer> RARITY = Map.of(
            "minecraft:glistering_melon_slice", 1,
            "minecraft:golden_carrot", 1,
            "minecraft:golden_apple", 1,
            "minecraft:enchanted_golden_apple", 2);

    public static final Map<String, Integer> STARS = Map.of(
            "minecraft:glistering_melon_slice", 1,
            "minecraft:golden_carrot", 1,
            "minecraft:copper_ingot", 1,
            "minecraft:iron_ingot", 1,
            "minecraft:golden_apple", 2,
            "minecraft:gold_ingot", 2,
            "minecraft:emerald", 2,
            "minecraft:diamond", 3,
            "minecraft:enchanted_golden_apple", 5);

    public static final Map<String, Integer> ENERGY = Map.of(
            "minecraft:glistering_melon_slice", 40,
            "minecraft:golden_carrot", 50,
            "minecraft:copper_ingot", 50,
            "minecraft:iron_ingot", 65,
            "minecraft:golden_apple", 75,
            "minecraft:gold_ingot", 80,
            "minecraft:emerald", 90,
            "minecraft:diamond", 100,
            "minecraft:enchanted_golden_apple", 100);

    public static final int TRIPLE = 3;
    public static final Map<String, String> BLOCKS = Map.of(
            "minecraft:copper_block", "minecraft:copper_ingot",
            "minecraft:iron_block", "minecraft:iron_ingot",
            "minecraft:gold_block", "minecraft:gold_ingot",
            "minecraft:emerald_block", "minecraft:emerald",
            "minecraft:diamond_block", "minecraft:diamond");
    public static final Map<String, String> TRIPLED = new HashMap<>();

    static {
        for (String berry : BERRY_TYPES.keySet()) {
            TRIPLED.put("hyperspace_wormholes:hyperspace_" + ResourceLocation.parse(berry).getPath(), berry);
        }
    }

    public static final List<String> TYPES = List.of("normal", "fire", "water", "grass", "electric", "ice", "fighting",
            "poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy");

    public static final String ESSENCE = "hyperspace_wormholes:legendary_essence";
    public static final String APPLE = "minecraft:enchanted_golden_apple";
    public static final String RANDOM = "random";
    public static final String ALL_TYPES = "all";
    private static final String CARRIED = "carried";
    private static final String RETYPED = "retyped";
    public static final Set<String> MINT_LEAVES = Set.of("cobblemon:red_mint_leaf", "cobblemon:blue_mint_leaf",
            "cobblemon:cyan_mint_leaf", "cobblemon:pink_mint_leaf", "cobblemon:green_mint_leaf", "cobblemon:white_mint_leaf");

    public static final int ESSENCE_SECONDS = 300;
    public static final float ALPHA_CHANCE = 0.05f;
    public static final float MINT_ALPHA_CHANCE = 0.05f;
    public static final float LEGENDARY_CHANCE = 0.01f;
    public static final float ESSENCE_LEGENDARY_CHANCE = 0.05f;

    public static final int BASE_ENERGY = 90;
    public static final int BERRY_ENERGY = 60;
    public static final int APPLE_SHINY_ROLLS = 20;

    private static final int WILD_BASE = 100;
    private static final int[][] STAR_BONUS = {{1, 20}, {1, 20}, {21, 35}, {36, 50}, {51, 65}, {66, 85}};
    private static final int[][] CAUGHT_LEVELS = {{13, 19}, {13, 19}, {15, 25}, {20, 35}, {35, 43}, {50, 56}};

    public static List<String> ids(ItemStack stack) {
        CompoundTag custom = custom(stack);
        List<String> ids = custom.getBoolean(RETYPED) ? new ArrayList<>() : ingredientIds(stack);
        ListTag carried = custom.getList(CARRIED, Tag.TAG_STRING);
        for (int i = 0; i < carried.size(); i++) ids.add(carried.getString(i));
        return ids;
    }

    private static List<String> ingredientIds(ItemStack stack) {
        List<String> ids = new ArrayList<>();
        IngredientComponent ingredients = stack.get(CobblemonItemComponents.INGREDIENT);
        if (ingredients == null) return ids;
        for (ResourceLocation key : ingredients.getIngredientIds()) {
            String id = key.toString(), base = TRIPLED.get(id);
            if (BLOCKS.containsKey(id)) ids.add(BLOCKS.get(id));
            if (base == null) ids.add(id);
            else ids.addAll(Collections.nCopies(TRIPLE, base));
        }
        return ids;
    }

    public static String baseBerry(String id) {
        String base = TRIPLED.getOrDefault(id, id);
        return BERRY_TYPES.containsKey(base) ? base : null;
    }

    public static ItemStack ring(int stars, int seconds, String type, String legendary, String mega, int shiny, int alpha) {
        CompoundTag boosts = new CompoundTag();
        boosts.putInt("stars", stars);
        boosts.putInt("calories", seconds);
        if (type != null) boosts.putString("type", type);
        if (legendary != null) boosts.putString("legendary", legendary);
        if (mega != null) boosts.putString("mega", mega);
        if (shiny > 1) boosts.putInt("shiny", shiny);
        if (alpha > 1) boosts.putInt("alpha", alpha);
        return save(new ItemStack(ModItems.HOOPA_RING), boosts);
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

    public static int stars(ItemStack stack) {
        CompoundTag custom = custom(stack);
        return custom.contains("stars") ? Math.min(5, custom.getInt("stars") + addedStars(ids(stack))) : stars(ids(stack));
    }

    public static int seconds(ItemStack stack) {
        CompoundTag custom = custom(stack);
        if (!custom.contains("calories")) return seconds(ids(stack));
        List<String> ids = ids(stack);
        return custom.getInt("calories") + Math.round(energy(ids) * (1 + 0.1f * stars(stack))) + flatSeconds(ids);
    }

    public static String legendary(ItemStack stack) {
        CompoundTag custom = custom(stack);
        if (!custom.contains("legendary")) return null;
        if (custom.contains("legendary", Tag.TAG_STRING)) return custom.getString("legendary");
        return custom.getBoolean("legendary") ? RANDOM : null;
    }

    public static ItemStack legendaryBoosts(ItemStack ring, float shiny, float alpha) {
        CompoundTag boosts = custom(ring);
        if (shiny > 0) boosts.putFloat("legendShiny", shiny);
        if (alpha > 0) boosts.putFloat("legendAlpha", alpha);
        return save(ring, boosts);
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
        for (String id : custom.getBoolean(RETYPED) ? ingredientIds(stack) : ids(stack)) {
            String type = BERRY_TYPES.get(id);
            if (type != null && !types.contains(type)) types.add(type);
        }
        return types;
    }

    public static int rarity(ItemStack stack) {
        return rarity(ids(stack));
    }

    public static int rarity(List<String> ids) {
        return ids.stream().mapToInt(id -> RARITY.getOrDefault(id, 0)).sum();
    }

    public static int stars(List<String> ids) {
        return Math.min(addedStars(ids), 5);
    }

    private static int addedStars(List<String> ids) {
        return ids.stream().mapToInt(id -> STARS.getOrDefault(id, 0)).sum();
    }

    public static int seconds(List<String> ids) {
        return Math.round((BASE_ENERGY + energy(ids)) * (1 + 0.1f * stars(ids))) + flatSeconds(ids);
    }

    private static int energy(List<String> ids) {
        int energy = 0;
        for (String id : ids) {
            if (BLOCKS.containsKey(id)) energy += (TRIPLE - 1) * ENERGY.get(BLOCKS.get(id));
            else energy += BERRY_TYPES.containsKey(id) ? BERRY_ENERGY : ENERGY.getOrDefault(id, 0);
        }
        return energy;
    }

    private static int flatSeconds(List<String> ids) {
        return ESSENCE_SECONDS * Collections.frequency(ids, ESSENCE);
    }

    public static void carryOver(ItemStack from, ItemStack to) {
        CompoundTag boosts = custom(from);
        ListTag carried = boosts.getList(CARRIED, Tag.TAG_STRING);
        if (!boosts.getBoolean(RETYPED)) {
            for (String id : ingredientIds(from)) carried.add(StringTag.valueOf(id));
        }
        boosts.put(CARRIED, carried);
        boosts.putBoolean(RETYPED, true);
        boosts.remove("type");
        save(to, boosts);
        to.remove(CobblemonItemComponents.INGREDIENT);
    }

    private static MutableComponent typeName(String name) {
        ElementalType type = ElementalTypes.get(name);
        return type == null ? Component.literal(name) : type.getDisplayName().copy().withColor(type.getHue());
    }

    public static Component seasoningLore(String id) {
        List<Component> parts = new ArrayList<>();
        int times = TRIPLED.containsKey(id) || BLOCKS.containsKey(id) ? TRIPLE : 1;
        id = BLOCKS.getOrDefault(id, TRIPLED.getOrDefault(id, id));
        if (BERRY_TYPES.containsKey(id)) {
            parts.add(typeName(BERRY_TYPES.get(id)));
            parts.add(Component.literal("+" + BERRY_ENERGY * times + " Calories"));
        }
        if (STARS.containsKey(id)) parts.add(Component.literal("+" + STARS.get(id) + "★").withStyle(ChatFormatting.GOLD));
        if (ENERGY.containsKey(id)) parts.add(Component.literal("+" + ENERGY.get(id) * times + " Calories"));
        if (RARITY.containsKey(id)) {
            parts.add(Component.literal("Rarer Pokemon"));
            int shiny = 3 * RARITY.get(id) + (id.equals(APPLE) ? APPLE_SHINY_ROLLS : 0);
            parts.add(Component.literal("+" + shiny + "x Shiny Chance"));
        }
        if (MINT_LEAVES.contains(id)) parts.add(Component.literal("+" + Math.round(MINT_ALPHA_CHANCE * 100) + "% Alpha Chance"));
        if (id.equals(ESSENCE)) {
            parts.add(Component.literal(Math.round(ESSENCE_LEGENDARY_CHANCE * 100) + "% Legendary Chance"));
            parts.add(Component.literal("+" + ESSENCE_SECONDS + " Calories"));
        }
        if (parts.isEmpty()) return null;
        MutableComponent line = Component.literal("Donut: ").withStyle(ChatFormatting.GRAY);
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) line.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            MutableComponent part = parts.get(i).copy();
            if (part.getStyle().getColor() == null) part.withStyle(ChatFormatting.LIGHT_PURPLE);
            line.append(part);
        }
        return line;
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
        tooltip.add(Component.translatable("tooltip.hyperspace_wormholes.ring.calories", seconds(stack)).withStyle(ChatFormatting.GRAY));
        CompoundTag custom = custom(stack);
        List<String> types = types(stack);
        boolean all = custom.getString("type").equals(ALL_TYPES);
        String key = !all && types.size() > 1 ? "types" : "type";
        MutableComponent line = Component.translatable("tooltip.hyperspace_wormholes.ring." + key).withStyle(ChatFormatting.GRAY);
        if (all) {
            line.append(Component.literal("All").withStyle(ChatFormatting.WHITE));
        } else if (types.isEmpty()) {
            line.append(Component.translatable("tooltip.hyperspace_wormholes.ring.random").withStyle(ChatFormatting.WHITE));
        } else {
            for (int i = 0; i < types.size(); i++) {
                if (i > 0) line.append(Component.literal(" / ").withStyle(ChatFormatting.GRAY));
                line.append(typeName(types.get(i)));
            }
        }
        tooltip.add(line);
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT || !Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.hyperspace_wormholes.ring.more").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        int total = seconds(stack);
        int stars = Math.max(1, stars(stack));
        tooltip.add(Component.translatable("tooltip.hyperspace_wormholes.ring.time", total / 60 + "m " + total % 60 + "s").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hyperspace_wormholes.ring.levels", minLevel(stars), maxLevel(stars)).withStyle(ChatFormatting.GRAY));
        List<String> modifiers = new ArrayList<>();
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.US);
        int shiny = shinyRolls(stack);
        if (shiny > 1) modifiers.add(format.format(shiny) + "x Shiny Chance");
        List<String> ids = ids(stack);
        int alpha = custom.contains("alpha") ? custom.getInt("alpha") + mints(ids) : Math.round(alphaChance(stack) / ALPHA_CHANCE);
        if (alpha > 1) modifiers.add(format.format(alpha) + "x Alpha Chance");
        if (ids.contains(ESSENCE)) modifiers.add(Math.round(ESSENCE_LEGENDARY_CHANCE * 100) + "% Legendary Chance");
        float legendShiny = legendaryShiny(stack), legendAlpha = legendaryAlpha(stack);
        if (legendShiny >= 1) modifiers.add("Shiny Legendary");
        else if (legendShiny > 0) modifiers.add(Math.round(legendShiny * 100) + "% Shiny Legendary");
        if (legendAlpha >= 1) modifiers.add("Alpha Legendary");
        else if (legendAlpha > 0) modifiers.add(Math.round(legendAlpha * 100) + "% Alpha Legendary");
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

    public static int shinyRolls(int rarity) {
        return 1 + 3 * rarity;
    }

    public static float alphaChance(ItemStack stack) {
        CompoundTag custom = custom(stack);
        int boost = custom.contains("alpha") ? custom.getInt("alpha") : 1;
        return Math.min(1f, ALPHA_CHANCE * boost + MINT_ALPHA_CHANCE * mints(ids(stack)));
    }

    private static int mints(List<String> ids) {
        return (int) ids.stream().filter(MINT_LEAVES::contains).count();
    }

    public static float legendaryChance(ItemStack stack) {
        if (ids(stack).contains(ESSENCE)) return ESSENCE_LEGENDARY_CHANCE;
        return stars(stack) >= 5 ? LEGENDARY_CHANCE : 0f;
    }

    public static int shinyRolls(ItemStack stack) {
        CompoundTag custom = custom(stack);
        List<String> ids = ids(stack);
        int base = custom.contains("shiny") ? custom.getInt("shiny") : 1;
        return base + 3 * rarity(ids) + APPLE_SHINY_ROLLS * Collections.frequency(ids, APPLE);
    }
}
