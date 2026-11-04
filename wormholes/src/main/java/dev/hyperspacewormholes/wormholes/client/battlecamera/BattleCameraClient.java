package dev.hyperspacewormholes.wormholes.client.battlecamera;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The battle camera's client entry point: BattleCamera's tick and its toggle, "battleCamera" in
 * config/horizons_movefx_camera.json (on by default), written with its default when missing.
 */
public final class BattleCameraClient implements ClientModInitializer {
    private static final String FILE = "horizons_movefx_camera.json";
    private static boolean enabled = true;

    @Override
    public void onInitializeClient() {
        loadConfig();
        ClientTickEvents.END_CLIENT_TICK.register(BattleCamera::tick);
    }

    public static boolean enabled() {
        return enabled;
    }

    private static void loadConfig() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE);
        JsonObject json = new JsonObject();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement read = JsonParser.parseReader(reader);
                if (read.isJsonObject()) json = read.getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                json = new JsonObject();
            }
        }
        enabled = !json.has("battleCamera") || json.get("battleCamera").getAsBoolean();
        if (json.has("battleCamera")) return;
        json.addProperty("battleCamera", enabled);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
