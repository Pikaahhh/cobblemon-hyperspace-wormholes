package dev.hyperspacewormholes.wormholes.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The boss Pokemon defaults, config/hyperspace_wormholes_boss.json (written on first start), for whatever a boss's
 * BossDef leaves out: max HP is the normal max HP times hpPerTrainer times the trainers in the fight (1 to
 * maxTrainers), scale resizes the model, the catch phase starts when HP is at or under catchBelow of the max (0: at 1
 * HP) and lasts catchSeconds, aiSkill is Cobblemon's battle AI level for its attacks (5, the smartest), and a boss
 * nobody has fought for emptyHealSeconds heals to full.
 */
public final class BossConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static BossConfig instance = new BossConfig();

    public double hpPerTrainer = 5.0;
    public float scale = 1.5f;
    public int maxTrainers = 4;
    public double catchBelow = 0.0;
    public int catchSeconds = 180;
    public int aiSkill = 5;
    public int emptyHealSeconds = 120;

    public static BossConfig get() {
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(Wormholes.MOD_ID + "_boss.json");
        try {
            if (Files.exists(path)) {
                BossConfig loaded = GSON.fromJson(Files.readString(path), BossConfig.class);
                if (loaded != null) instance = loaded;
            }
            Files.writeString(path, GSON.toJson(instance));
        } catch (IOException | RuntimeException e) {
            Wormholes.LOGGER.error("Failed to read {}, using defaults", path, e);
        }
    }
}
