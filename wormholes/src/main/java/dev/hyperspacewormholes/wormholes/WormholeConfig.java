package dev.hyperspacewormholes.wormholes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The config file, config/hyperspace_wormholes.json, which says which Pokemon can be ridden into a wormhole.
 */
public final class WormholeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static WormholeConfig instance = new WormholeConfig();

    public boolean requireMount = true;
    public List<String> allowedMounts = List.of("cobblemon:solgaleo", "cobblemon:lunala", "cobblemon:necrozma");

    public static WormholeConfig get() {
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(Wormholes.MOD_ID + ".json");
        try {
            if (Files.exists(path)) {
                WormholeConfig loaded = GSON.fromJson(Files.readString(path), WormholeConfig.class);
                if (loaded != null) instance = loaded;
            }
            Files.writeString(path, GSON.toJson(instance));
        } catch (IOException | RuntimeException e) {
            Wormholes.LOGGER.error("Failed to read {}, using defaults", path, e);
        }
    }
}
