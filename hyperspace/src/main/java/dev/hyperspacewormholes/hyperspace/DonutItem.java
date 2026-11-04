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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The donut item. Keeps its berries, icing, sprinkles and glint on the stack. Like Legends Z-A, the icing and name come
 * from the donut's strongest flavour (Meringue, Curry, Jam, Chocolate, Cream, or Rainbow on a tie) and the berry it has
 * the most of, and the name and tooltip show its stars.
 */
public class DonutItem extends Item {
    public static final List<String> ICINGS = List.of(
            "plain", "orangey", "pink", "lime", "green", "violet", "blue", "skyblue", "aqua", "deep_blue",
            "inverted_orangey", "inverted_pink", "inverted_lime", "inverted_green", "inverted_violet", "inverted_blue",
            "inverted_skyblue", "inverted_aqua", "inverted_deep_blue",
            "meringue", "curry", "jam", "chocolate", "cream", "rainbow");

    public static final List<String> SPRINKLE_ORDER = List.of(
            "minecraft:diamond", "minecraft:emerald", "minecraft:gold_ingot", "minecraft:iron_ingot", "minecraft:copper_ingot");

    public static final Map<String, Integer> SPRINKLES = Map.of(
            "minecraft:diamond", 0x4AEDD9, "minecraft:emerald", 0x17DD62, "minecraft:gold_ingot", 0xFDF55F,
            "minecraft:iron_ingot", 0xE0E0E0, "minecraft:copper_ingot", 0xE77C56);

    public DonutItem() {
        super(new Properties());
    }

    public static List<ResourceLocation> berries(ItemStack stack) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String id : DonutBoosts.ids(stack)) {
            if (DonutBoosts.isBerry(id)) counts.merge(id, 1, Integer::sum);
        }
        List<String> ids = new ArrayList<>(counts.keySet());
        ids.sort((a, b) -> counts.get(b) - counts.get(a));
        return ids.stream().map(ResourceLocation::parse).toList();
    }

    public static int icing(ItemStack stack) {
        String kind = DonutBoosts.kind(DonutBoosts.ids(stack));
        return kind == null ? 0 : Math.max(0, ICINGS.indexOf(kind));
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
        String kind = DonutBoosts.kind(DonutBoosts.ids(stack));
        MutableComponent name = list.isEmpty() || kind == null ? Component.translatable(getDescriptionId())
                : Component.translatable(getDescriptionId() + "." + kind,
                        Component.translatable(BuiltInRegistries.ITEM.get(list.get(0)).getDescriptionId()));
        int stars = DonutBoosts.stars(stack);
        if (stars > 0) name.append(Component.literal(" " + DonutBoosts.starText(stars)).withStyle(ChatFormatting.GOLD));
        return name;
    }
}
