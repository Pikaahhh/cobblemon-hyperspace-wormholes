package dev.hyperspacewormholes.hyperspace;

import com.cobblemon.mod.common.api.drop.DropTable;
import com.cobblemon.mod.common.api.drop.ItemDropEntry;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;
import kotlin.ranges.IntRange;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Makes every Flying egg group Pokemon drop vanilla eggs. Gets reapplied whenever the server starts or datapacks
 * reload.
 */
public class BirdEggs {
    public static float percentage = 15f;
    public static int maxEggs = 4;
    private static final ResourceLocation EGG = ResourceLocation.withDefaultNamespace("egg");
    private static final Set<DropTable> done = Collections.newSetFromMap(new WeakHashMap<>());

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> addEggs());
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> addEggs());
    }

    private static void addEggs() {
        for (Species species : PokemonSpecies.getSpecies()) {
            if (species.getEggGroups().contains(EggGroup.FLYING)) add(species.getDrops());
            for (FormData form : species.getForms()) {
                if (form.getEggGroups().contains(EggGroup.FLYING)) add(form.getDrops());
            }
        }
    }

    private static void add(DropTable table) {
        if (!done.add(table)) return;
        ItemDropEntry egg = new ItemDropEntry();
        egg.setItem(EGG);
        egg.setPercentage(percentage);
        egg.setQuantityRange(new IntRange(0, maxEggs));
        table.getEntries().add(egg);
    }
}
