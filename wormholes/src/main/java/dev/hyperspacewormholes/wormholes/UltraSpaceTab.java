package dev.hyperspacewormholes.wormholes;

import dev.hyperspacewormholes.wormholes.ride.ItemAnalyser;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * The Ultra Space creative tab with every wormholes item. Also loads those item classes so they actually get
 * registered.
 */
public final class UltraSpaceTab {
    public static void register() {
        List<Item> before = List.of(Flutes.POKE_FLUTE, Flutes.SUN_FLUTE, Flutes.MOON_FLUTE, CosmicJar.ITEM);
        List<Item> after = List.of(UltraHide.ITEM, BigBattery.ITEM, ItemAnalyser.ERROR_SCREEN);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Wormholes.id("ultra_space"), FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.hyperspace_wormholes.ultra_space"))
                .icon(CosmicJar::full)
                .displayItems((params, output) -> {
                    before.forEach(output::accept);
                    output.accept(CosmicJar.full());
                    after.forEach(output::accept);
                })
                .build());
    }
}
