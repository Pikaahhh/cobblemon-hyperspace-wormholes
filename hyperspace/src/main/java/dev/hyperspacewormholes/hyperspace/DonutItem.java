package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.CobblemonItemComponents;
import com.cobblemon.mod.common.item.components.IngredientComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Map;

/**
 * The donut item. Keeps its berries, icing, sprinkles and glint on the stack, and the name and tooltip show what Hoopa
 * Ring stars it'll give you.
 */
public class DonutItem extends Item {
    public static final List<String> ICINGS = List.of(
            "plain", "orangey", "pink", "lime", "green", "violet", "blue", "skyblue", "aqua", "deep_blue",
            "inverted_orangey", "inverted_pink", "inverted_lime", "inverted_green", "inverted_violet", "inverted_blue",
            "inverted_skyblue", "inverted_aqua", "inverted_deep_blue");

    public static final Map<String, String> ICING = Map.ofEntries(
            Map.entry("cobblemon:occa_berry", "orangey"), Map.entry("cobblemon:shuca_berry", "orangey"),
            Map.entry("cobblemon:charti_berry", "orangey"), Map.entry("cobblemon:wacan_berry", "orangey"),
            Map.entry("cobblemon:chilan_berry", "orangey"), Map.entry("cobblemon:chople_berry", "pink"),
            Map.entry("cobblemon:haban_berry", "pink"), Map.entry("cobblemon:roseli_berry", "pink"),
            Map.entry("cobblemon:rindo_berry", "lime"), Map.entry("cobblemon:babiri_berry", "lime"),
            Map.entry("cobblemon:kebia_berry", "green"), Map.entry("cobblemon:tanga_berry", "green"),
            Map.entry("cobblemon:payapa_berry", "violet"), Map.entry("cobblemon:colbur_berry", "violet"),
            Map.entry("cobblemon:passho_berry", "blue"), Map.entry("cobblemon:coba_berry", "skyblue"),
            Map.entry("cobblemon:yache_berry", "aqua"), Map.entry("cobblemon:kasib_berry", "deep_blue"));

    public static final List<String> SPRINKLE_ORDER = List.of(
            "minecraft:diamond", "minecraft:emerald", "minecraft:gold_ingot", "minecraft:iron_ingot", "minecraft:copper_ingot");

    public static final Map<String, Integer> SPRINKLES = Map.of(
            "minecraft:diamond", 0x4AEDD9, "minecraft:emerald", 0x17DD62, "minecraft:gold_ingot", 0xFDF55F,
            "minecraft:iron_ingot", 0xE0E0E0, "minecraft:copper_ingot", 0xE77C56);

    public DonutItem() {
        super(new Properties());
    }

    public static List<ResourceLocation> berries(ItemStack stack) {
        IngredientComponent ingredients = stack.get(CobblemonItemComponents.INGREDIENT);
        if (ingredients == null) return List.of();
        return ingredients.getIngredientIds().stream().filter(id -> DonutBoosts.baseBerry(id.toString()) != null)
                .distinct().toList();
    }

    public static int icing(ItemStack stack) {
        List<ResourceLocation> list = berries(stack);
        if (list.isEmpty()) return 0;
        String berry = list.get(0).toString();
        String icing = ICING.get(DonutBoosts.baseBerry(berry));
        if (icing == null) return 0;
        return Math.max(0, ICINGS.indexOf(DonutBoosts.TRIPLED.containsKey(berry) ? "inverted_" + icing : icing));
    }

    public static int sprinkles(ItemStack stack) {
        List<String> ids = DonutBoosts.ids(stack);
        for (String ore : SPRINKLE_ORDER) {
            if (ids.contains(ore)) return SPRINKLES.get(ore);
        }
        return -1;
    }

    public static boolean enchanted(ItemStack stack) {
        return DonutBoosts.ids(stack).contains(DonutBoosts.APPLE);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return enchanted(stack) || super.isFoil(stack);
    }

    public static ItemStack withBerry(Item berry, int count) {
        ItemStack stack = new ItemStack(ModItems.DONUT, count);
        stack.set(CobblemonItemComponents.INGREDIENT, new IngredientComponent(List.of(BuiltInRegistries.ITEM.getKey(berry))));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DonutBoosts.addLore(stack, tooltip);
    }

    @Override
    public Component getName(ItemStack stack) {
        List<ResourceLocation> list = berries(stack);
        MutableComponent name = switch (list.size()) {
            case 0 -> Component.translatable(getDescriptionId());
            case 1 -> Component.translatable(getDescriptionId() + ".berry",
                    BuiltInRegistries.ITEM.get(list.get(0)).getDescription());
            default -> Component.translatable(getDescriptionId() + ".mixed");
        };
        int stars = DonutBoosts.stars(stack);
        if (stars > 0) name.append(Component.literal(" " + DonutBoosts.starText(stars)).withStyle(ChatFormatting.GOLD));
        return name;
    }
}
