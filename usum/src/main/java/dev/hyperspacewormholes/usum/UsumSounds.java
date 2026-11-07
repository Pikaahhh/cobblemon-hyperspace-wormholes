package dev.hyperspacewormholes.usum;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/**
 * Whether this jar carries extra move sounds, the asset namespace {@value #NAMESPACE} (sounds.json plus its oggs).
 * Its sound event names are read once from the
 * jar's own sounds.json (looked up through the mod it ships in, MoveFx.MOD_ID); an empty set means there are none, and the timelines' stand-in sounds play instead
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
            events = found;
        }
        return events;
    }

    public static boolean available() {
        return !events().isEmpty();
    }
}
