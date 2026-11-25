package dev.hyperspacewormholes.wormholes.client.battlecamera;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The battle camera's client entry point: BattleCamera's tick and its toggle, "battleCamera" in
 * config/horizons_movefx_camera.json (on by default), written with its default when missing.
 */
public final class BattleCameraClient {
    private static final String FILE = "horizons_movefx_camera.json";
    private static boolean enabled = true;

    public static void init(IEventBus modBus) {
        loadConfig();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> BattleCamera.tick(Minecraft.getInstance()));
    }

    public static boolean enabled() {
        return enabled;
    }

    private static void loadConfig() {
        Path file = FMLPaths.CONFIGDIR.get().resolve(FILE);
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
