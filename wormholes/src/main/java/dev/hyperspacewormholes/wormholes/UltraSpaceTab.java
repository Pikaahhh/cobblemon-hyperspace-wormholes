package dev.hyperspacewormholes.wormholes;

import dev.hyperspacewormholes.wormholes.ride.ItemAnalyser;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;

/** The Ultra Space creative tab with every wormholes item; it also loads the item classes so they get registered. */
public final class UltraSpaceTab {
    public static void register() {
        Item jar = CosmicJar.ITEM;
        List<Item> after = List.of(BigBattery.ITEM, LuminescentIce.ITEM, ItemAnalyser.ERROR_SCREEN);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Wormholes.id("ultra_space"), FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.hyperspace_wormholes.ultra_space"))
                .icon(CosmicJar::full)
                .displayItems((params, output) -> {
                    output.accept(jar);
                    output.accept(CosmicJar.full());
                    after.forEach(output::accept);
                })
                .build());
    }
}
