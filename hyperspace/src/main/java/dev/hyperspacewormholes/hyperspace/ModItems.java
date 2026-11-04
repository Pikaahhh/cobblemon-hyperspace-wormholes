package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.pokemon.helditem.CobblemonHeldItemManager;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Registers the Hyperspace items (Distortion Splicer, donut, display items, berries) and the creative tab. */
public class ModItems {
    public static final Item DISTORTION_SPLICER = register("distortion_splicer", new DistortionSplicer(new Item.Properties()));
    public static final DonutItem DONUT = register("donut", new DonutItem());
    public static final Item HOOPA_PORTAL = register("hoopa_portal", new Item(new Item.Properties().stacksTo(1)));
    public static final Item HOOPA_PORTAL_CLOSED = register("hoopa_portal_closed", new Item(new Item.Properties().stacksTo(1)));
    public static final Item HOOPA_THROWN_RING = register("hoopa_thrown_ring", new Item(new Item.Properties().stacksTo(1)));
    public static final Map<String, Item> HYPERSPACE_BERRIES = new LinkedHashMap<>();

    static {
        for (String type : DonutBoosts.TYPES) {
            for (Map.Entry<String, String> berry : DonutBoosts.BERRY_TYPES.entrySet()) {
                if (!berry.getValue().equals(type)) continue;
                String path = ResourceLocation.parse(berry.getKey()).getPath();
                Item item = register("hyperspace_" + path, new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
                HYPERSPACE_BERRIES.put(type, item);
                CobblemonHeldItemManager.INSTANCE.registerRemap(item, path.replace("_", ""));
            }
        }
    }

    public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Hyperspace.id("hyperspace"));

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.hyperspace_wormholes.hyperspace"))
                .icon(() -> new ItemStack(DISTORTION_SPLICER))
                .displayItems((params, output) -> {
                    output.accept(DISTORTION_SPLICER);
                    output.accept(DONUT);
                    for (Item berry : HYPERSPACE_BERRIES.values()) output.accept(berry);
                })
                .build());
    }

    private static <T extends Item> T register(String name, T item) {
        return Registry.register(BuiltInRegistries.ITEM, Hyperspace.id(name), item);
    }
}
