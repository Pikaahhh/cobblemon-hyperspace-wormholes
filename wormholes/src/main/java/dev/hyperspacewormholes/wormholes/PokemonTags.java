package dev.hyperspacewormholes.wormholes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pokemon tags from datapacks (data/namespace/tags/pokemon/name.json). Same shape as vanilla tags, with forms or
 * aspects after each species. Datapacks can add to our defaults or replace them.
 */
public final class PokemonTags implements ResourceManagerReloadListener {
    private static final String DIRECTORY = "tags/pokemon";
    private static Map<ResourceLocation, List<String>> tags = Map.of();

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new PokemonTags()));
    }

    public static List<String> get(ResourceLocation tag) {
        return tags.getOrDefault(tag, List.of());
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<ResourceLocation, List<String>> loaded = new HashMap<>();
        manager.listResourceStacks(DIRECTORY, path -> path.getPath().endsWith(".json")).forEach((file, stack) -> {
            String path = file.getPath();
            ResourceLocation tag = ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
                    path.substring(DIRECTORY.length() + 1, path.length() - ".json".length()));
            List<String> values = new ArrayList<>();
            for (Resource resource : stack) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("replace") && json.get("replace").getAsBoolean()) values.clear();
                    for (JsonElement value : json.getAsJsonArray("values")) {
                        if (!values.contains(value.getAsString())) values.add(value.getAsString());
                    }
                } catch (IOException | RuntimeException e) {
                    Wormholes.LOGGER.error("Couldn't read Pokémon tag {} from {}", tag, resource.sourcePackId(), e);
                }
            }
            loaded.put(tag, List.copyOf(values));
        });
        tags = loaded;
    }
}
