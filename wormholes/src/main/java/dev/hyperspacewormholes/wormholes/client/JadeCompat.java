package dev.hyperspacewormholes.wormholes.client;

import dev.hyperspacewormholes.wormholes.Wormholes;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Hides Jade's tooltip during an Ultra Warp Ride and brings it back after. Done with reflection so Jade stays
 * optional.
 */
final class JadeCompat {
    private static boolean hiddenByUs;
    private static boolean failed;

    private JadeCompat() {}

    static void hide() {
        if (hiddenByUs) return;
        Object general = general();
        if (general == null) return;
        try {
            if (!(boolean) general.getClass().getMethod("shouldDisplayTooltip").invoke(general)) return;
            setDisplay(general, false);
            hiddenByUs = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
        }
    }

    static void restore() {
        if (!hiddenByUs) return;
        hiddenByUs = false;
        Object general = general();
        if (general == null) return;
        try {
            setDisplay(general, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
        }
    }

    private static void setDisplay(Object general, boolean display) throws ReflectiveOperationException {
        Class.forName("snownee.jade.api.config.IWailaConfig$IConfigGeneral")
                .getMethod("setDisplayTooltip", boolean.class).invoke(general, display);
    }

    private static Object general() {
        if (failed || !FabricLoader.getInstance().isModLoaded("jade")) return null;
        try {
            Class<?> config = Class.forName("snownee.jade.api.config.IWailaConfig");
            Object instance = config.getMethod("get").invoke(null);
            return config.getMethod("getGeneral").invoke(instance);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return null;
        }
    }

    private static void fail(Exception e) {
        if (!failed) Wormholes.LOGGER.warn("Couldn't toggle Jade's tooltip for the ride", e);
        failed = true;
    }
}
