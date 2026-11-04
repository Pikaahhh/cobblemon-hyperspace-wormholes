package dev.hyperspacewormholes.hyperspace;

import net.minecraft.advancements.AdvancementNode;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Where everything sits on the Hyperspace and Wormholes advancement pages, in vanilla's units (a tile is 28 by 27
 * pixels), used by AdvancementLayoutMixin in place of vanilla's tidy columns.
 * Hyperspace fans out from Enter Hyperspace and Wormholes zigzags along its one long chain; an advancement with no spot
 * here keeps vanilla's.
 */
public final class AdvancementLayout {
    private static final Map<String, float[]> SPOTS = Map.ofEntries(
            Map.entry("hyperspace/root", new float[]{0f, 0f}),
            Map.entry("hyperspace/enter_hyperspace", new float[]{1.5f, 0f}),
            Map.entry("hyperspace/legendary_encounter", new float[]{2.9f, -2.2f}),
            Map.entry("hyperspace/hoopa_unbound", new float[]{4.5f, -2.6f}),
            Map.entry("hyperspace/untapped_power", new float[]{3.7f, -1.1f}),
            Map.entry("hyperspace/taskmaster", new float[]{4.1f, 0f}),
            Map.entry("hyperspace/quite_dazzling", new float[]{3.7f, 1.1f}),
            Map.entry("hyperspace/poppin_time", new float[]{2.9f, 2.2f}),
            Map.entry("hyperspace/golden_glow", new float[]{4.5f, 2.3f}),
            Map.entry("hyperspace/professional_popper", new float[]{4.1f, 3.4f}),
            Map.entry("wormholes/root", new float[]{0f, 0f}),
            Map.entry("wormholes/magical_summoner", new float[]{1.6f, -1.4f}),
            Map.entry("wormholes/cosmic_filling", new float[]{3.2f, 0f}),
            Map.entry("wormholes/sun_and_moon", new float[]{4.8f, -1.4f}),
            Map.entry("wormholes/ride_of_your_life", new float[]{6.4f, 0f}),
            Map.entry("wormholes/maximum_rings", new float[]{8f, -1.4f}),
            Map.entry("wormholes/no_location_untouched", new float[]{8.4f, 0f}),
            Map.entry("wormholes/light_recaptured", new float[]{8f, 1.4f}));

    private AdvancementLayout() {
    }

    public static void place(AdvancementNode node) {
        ResourceLocation id = node.holder().id();
        float[] spot = id.getNamespace().equals(Hyperspace.MOD_ID) ? SPOTS.get(id.getPath()) : null;
        if (spot != null) node.advancement().display().ifPresent(display -> display.setLocation(spot[0], spot[1]));
        for (AdvancementNode child : node.children()) place(child);
    }
}
