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

/**
 * Registers the Hyperspace items (Hoopa Ring, donut, the portal display items, Legendary Essence and the Hyperspace
 * berries, which work like the normal ones when held in battle) and the creative tab.
 */
public class ModItems {
    public static final Item HOOPA_RING = register("hoopa_ring", new HoopaRingItem());
    public static final DonutItem DONUT = register("donut", new DonutItem());
    public static final Item HOOPA_PORTAL = register("hoopa_portal", new Item(new Item.Properties().stacksTo(1)));
    public static final Item HOOPA_PORTAL_CLOSED = register("hoopa_portal_closed", new Item(new Item.Properties().stacksTo(1)));
    public static final Item LEGENDARY_ESSENCE = register("legendary_essence", new Item(new Item.Properties().rarity(Rarity.EPIC)));
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

    public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("hyperspace"));

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.hyperspace_wormholes.hyperspace"))
                .icon(() -> new ItemStack(HOOPA_RING))
                .displayItems((params, output) -> {
                    output.accept(HOOPA_RING);
                    output.accept(DONUT);
                    output.accept(LEGENDARY_ESSENCE);
                    for (Item berry : HYPERSPACE_BERRIES.values()) output.accept(berry);
                })
                .build());
    }

    private static <T extends Item> T register(String name, T item) {
        return Registry.register(BuiltInRegistries.ITEM, id(name), item);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Hyperspace.MOD_ID, path);
    }
}
