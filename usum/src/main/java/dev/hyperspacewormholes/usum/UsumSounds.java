package dev.hyperspacewormholes.usum;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/**
 * Whether this jar carries the real Ultra Sun move sounds: the git-excluded asset namespace {@value #NAMESPACE}
 * (sounds.json plus its oggs), which only a -PwithUsum build packs. Its sound event names are read once from the
 * jar's own sounds.json (looked up through the mod it ships in, MoveFx.MOD_ID); an empty set means a plain build, where the timelines' stand-in sounds play instead
 * (UsumSoundKeyframe, StandInKeyframe).
 */
public final class UsumSounds {
    public static final String NAMESPACE = "horizons_movefx_usum";

    private static Set<String> events;

    private UsumSounds() {
    }

    public static synchronized Set<String> events() {
        if (events == null) {
            Set<String> found = new HashSet<>();
            FabricLoader.getInstance().getModContainer(MoveFx.MOD_ID)
                    .flatMap(mod -> mod.findPath("assets/" + NAMESPACE + "/sounds.json"))
                    .ifPresent(file -> {
                        try (Reader reader = Files.newBufferedReader(file)) {
                            found.addAll(JsonParser.parseReader(reader).getAsJsonObject().keySet());
                        } catch (Exception e) {
                            LoggerFactory.getLogger(MoveFx.NAMESPACE).warn("[movefx] unreadable {} sounds.json", NAMESPACE, e);
                        }
                    });
            LoggerFactory.getLogger(MoveFx.NAMESPACE).info("[movefx] Ultra Sun move sounds: {} events{}", found.size(),
                    found.isEmpty() ? " (plain build, stand-in sounds)" : "");
            events = found;
        }
        return events;
    }

    public static boolean available() {
        return !events().isEmpty();
    }
}
