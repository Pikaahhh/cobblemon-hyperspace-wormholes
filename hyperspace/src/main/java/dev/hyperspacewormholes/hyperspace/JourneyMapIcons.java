package dev.hyperspacewormholes.hyperspace;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Puts the map icons (MapIcons) on JourneyMap's minimap and fullscreen map as marker overlays: a distortion icon at
 * each spatial distortion in the Overworld, titled like the Xaero maps' hover lines. Display only; the markers are put
 * up again when the list changes, on a new world and whenever JourneyMap has dropped them (checked every second).
 * It all goes through JourneyMap's client API by reflection (journeymap.api.client.impl.ClientAPI.INSTANCE, which takes
 * overlays from any mod id), so JourneyMap isn't needed to build or run.
 */
public final class JourneyMapIcons {
    private static final String IMPL = "journeymap.api.client.impl.ClientAPI";
    private static final String API = "journeymap.api.v2.client.";
    private static final int CHECK_EVERY = 20;
    private static boolean failed;
    private static Object api;
    private static Method show, removeAll, exists, setDimension, setTitle, setOrder, centre;
    private static Constructor<?> marker, image;
    private static Object shownFor, probe;
    private static int ticks;

    private JourneyMapIcons() {}

    static void tick(Minecraft minecraft) {
        if (failed) return;
        try {
            if (minecraft.level == null) {
                if (shownFor != null && api != null) removeAll.invoke(api, Hyperspace.MOD_ID);
                shownFor = probe = null;
                return;
            }
            List<Object> now = List.of(MapIcons.marks(Level.OVERWORLD), minecraft.level);
            boolean dropped = probe != null && ++ticks % CHECK_EVERY == 0 && !(Boolean) exists.invoke(api, probe);
            if (now.equals(shownFor) && !dropped) return;
            if (api == null) lookUp();
            shownFor = now;
            put();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            Hyperspace.LOGGER.warn("Couldn't put map icons on JourneyMap; leaving them off", e);
        }
    }

    private static void put() throws ReflectiveOperationException {
        removeAll.invoke(api, Hyperspace.MOD_ID);
        probe = null;
        for (MapDistortions.Mark mark : MapIcons.marks(Level.OVERWORLD)) show(mark.pos(), MapIcons.tooltip(mark));
    }

    private static void show(BlockPos pos, List<Component> lines) throws ReflectiveOperationException {
        Object icon = image.newInstance(MapIcons.DISTORTION, 16, 16);
        centre.invoke(icon);
        Object overlay = marker.newInstance(Hyperspace.MOD_ID, pos, icon);
        setDimension.invoke(overlay, Level.OVERWORLD);
        setTitle.invoke(overlay, lines.stream().map(Component::getString).collect(Collectors.joining("\n")));
        setOrder.invoke(overlay, 100);
        show.invoke(api, overlay);
        if (probe == null) probe = overlay;
    }

    private static void lookUp() throws ReflectiveOperationException {
        api = Class.forName(IMPL).getField("INSTANCE").get(null);
        Class<?> clientApi = Class.forName(API + "IClientAPI");
        Class<?> displayable = Class.forName(API + "display.Displayable");
        Class<?> overlay = Class.forName(API + "display.Overlay");
        Class<?> markerType = Class.forName(API + "display.MarkerOverlay");
        Class<?> imageType = Class.forName(API + "model.MapImage");
        show = clientApi.getMethod("show", displayable);
        removeAll = clientApi.getMethod("removeAll", String.class);
        exists = clientApi.getMethod("exists", displayable);
        setDimension = overlay.getMethod("setDimension", ResourceKey.class);
        setTitle = overlay.getMethod("setTitle", String.class);
        setOrder = overlay.getMethod("setDisplayOrder", int.class);
        centre = imageType.getMethod("centerAnchors");
        marker = markerType.getConstructor(String.class, BlockPos.class, imageType);
        image = imageType.getConstructor(ResourceLocation.class, int.class, int.class);
    }
}
