package dev.hyperspacewormholes.wormholes.boss;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Loads the boss definitions (BossDef) from the data packs, with config/hyperspace_wormholes/bosses files overriding or adding keys. */
public final class BossDefs {
    private static final String FOLDER = "bosses";
    private static final Map<ResourceLocation, BossDef> DEFS = new LinkedHashMap<>();

    private BossDefs() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> reload(event.getServer()));
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() == null) reload(event.getPlayerList().getServer());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> DEFS.clear());
    }

    public static @Nullable BossDef get(ResourceLocation id) {
        return DEFS.get(id);
    }

    public static @Nullable BossDef find(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name.contains(":") ? name : Wormholes.MOD_ID + ":" + name);
        return id == null ? null : DEFS.get(id);
    }

    public static List<ResourceLocation> ids() {
        return new ArrayList<>(DEFS.keySet());
    }

    public static int reload(MinecraftServer server) {
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : server.getResourceManager()
                .listResources(FOLDER, file -> file.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = entry.getKey();
            String path = file.getPath().substring(FOLDER.length() + 1, file.getPath().length() - ".json".length());
            try (Reader reader = entry.getValue().openAsReader()) {
                raw.put(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path), JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException | RuntimeException e) {
                Wormholes.LOGGER.error("[boss] couldn't read boss data {}", file, e);
            }
        }
        Path folder = FMLPaths.CONFIGDIR.get().resolve(Wormholes.MOD_ID).resolve(FOLDER);
        try {
            Files.createDirectories(folder);
            try (Stream<Path> files = Files.list(folder)) {
                for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                    String name = file.getFileName().toString();
                    name = name.substring(0, name.length() - ".json".length());
                    int split = name.indexOf("__");
                    ResourceLocation id = split > 0
                            ? ResourceLocation.tryParse(name.substring(0, split) + ":" + name.substring(split + 2))
                            : ResourceLocation.tryParse(Wormholes.MOD_ID + ":" + name);
                    if (id == null) {
                        Wormholes.LOGGER.warn("[boss] skipping override {}: not a valid id", file);
                        continue;
                    }
                    try {
                        JsonObject override = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                        JsonObject base = raw.computeIfAbsent(id, k -> new JsonObject());
                        for (Map.Entry<String, JsonElement> key : override.entrySet()) base.add(key.getKey(), key.getValue());
                    } catch (IOException | RuntimeException e) {
                        Wormholes.LOGGER.error("[boss] couldn't read boss override {}", file, e);
                    }
                }
            }
        } catch (IOException e) {
            Wormholes.LOGGER.error("[boss] couldn't read the boss override folder {}", folder, e);
        }
        DEFS.clear();
        for (Map.Entry<ResourceLocation, JsonObject> entry : raw.entrySet()) {
            try {
                DEFS.put(entry.getKey(), BossDef.parse(entry.getKey(), entry.getValue()));
            } catch (RuntimeException e) {
                Wormholes.LOGGER.error("[boss] boss data {} is invalid", entry.getKey(), e);
            }
        }
        Wormholes.LOGGER.info("[boss] loaded {} boss definitions: {}", DEFS.size(), DEFS.keySet());
        return DEFS.size();
    }
}
