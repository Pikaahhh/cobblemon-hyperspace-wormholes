package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.pokemon.helditem.CobblemonHeldItemManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Registers the Hyperspace items (Distortion Splicer, donut, display items, berries) and the creative tab. */
public class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hyperspace.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Hyperspace.MOD_ID);

    public static final DeferredItem<Item> DISTORTION_SPLICER = register("distortion_splicer", () -> new DistortionSplicer(new Item.Properties()));
    public static final DeferredItem<DonutItem> DONUT = register("donut", DonutItem::new);
    public static final DeferredItem<Item> HOOPA_PORTAL = register("hoopa_portal", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> HOOPA_PORTAL_CLOSED = register("hoopa_portal_closed", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> HOOPA_THROWN_RING = register("hoopa_thrown_ring", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final Map<String, Item> HYPERSPACE_BERRIES = new LinkedHashMap<>();

    static {
        for (String type : DonutBoosts.TYPES) {
            for (Map.Entry<String, String> berry : DonutBoosts.BERRY_TYPES.entrySet()) {
                if (!berry.getValue().equals(type)) continue;
                String path = ResourceLocation.parse(berry.getKey()).getPath();
                register("hyperspace_" + path, () -> {
                    Item item = new Item(new Item.Properties().rarity(Rarity.UNCOMMON));
                    HYPERSPACE_BERRIES.put(type, item);
                    CobblemonHeldItemManager.INSTANCE.registerRemap(item, path.replace("_", ""));
                    return item;
                });
            }
        }
    }

    public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Hyperspace.id("hyperspace"));

    public static void register(IEventBus modBus) {
        TABS.register(TAB.location().getPath(), () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.hyperspace_wormholes.hyperspace"))
                .icon(() -> new ItemStack(DISTORTION_SPLICER.get()))
                .displayItems((params, output) -> {
                    output.accept(DISTORTION_SPLICER);
                    output.accept(DONUT);
                    for (Item berry : HYPERSPACE_BERRIES.values()) output.accept(berry);
                })
                .build());
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    private static <T extends Item> DeferredItem<T> register(String name, Supplier<T> item) {
        return ITEMS.register(name, item);
    }
}
