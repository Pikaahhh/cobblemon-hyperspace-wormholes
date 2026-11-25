package dev.hyperspacewormholes.wormholes;

import dev.hyperspacewormholes.wormholes.ride.ItemAnalyser;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Supplier;

/** The Ultra Space creative tab with every wormholes item; it also registers the Cosmic Jar. */
public final class UltraSpaceTab {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Wormholes.MOD_ID);

    public static void register(IEventBus modBus) {
        CosmicJar.register(modBus);
        List<Supplier<? extends Item>> after = List.of(LuminescentIce.ITEM, ItemAnalyser.ERROR_SCREEN);
        TABS.register("ultra_space", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.hyperspace_wormholes.ultra_space"))
                .icon(CosmicJar::full)
                .displayItems((params, output) -> {
                    output.accept(CosmicJar.ITEM.get());
                    output.accept(CosmicJar.full());
                    after.forEach(item -> output.accept(item.get()));
                })
                .build());
        TABS.register(modBus);
    }
}
