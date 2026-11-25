package dev.hyperspacewormholes.hyperspace;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Method;

/** Turns Iris shaders off while you're in Hyperspace (shaderpacks and the grey look don't mix). */
public class IrisOff {
    private static Object config;
    private static Method enabled;
    private static Method apply;
    private static boolean turnedOff;

    public static void register() {
        if (!ModList.get().isLoaded("iris")) return;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            config = api.getMethod("getConfig").invoke(instance);
            Class<?> configType = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig");
            enabled = configType.getMethod("areShadersEnabled");
            apply = configType.getMethod("setShadersEnabledAndApply", boolean.class);
        } catch (Exception e) {
            Hyperspace.LOGGER.warn("Couldn't hook into Iris, shaders stay on in Hyperspace", e);
            return;
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> restore());
    }

    private static void restore() {
        if (config == null || !turnedOff) return;
        turnedOff = false;
        try {
            apply.invoke(config, true);
        } catch (Exception e) {
            Hyperspace.LOGGER.warn("Couldn't switch Iris shaders back on", e);
        }
    }

    private static void tick(Minecraft client) {
        if (config == null) return;
        boolean inside = client.level != null && client.level.dimension().equals(Hyperspace.HYPERSPACE);
        try {
            if (inside && (boolean) enabled.invoke(config)) {
                apply.invoke(config, false);
                turnedOff = true;
            } else if (!inside) {
                restore();
            }
        } catch (Exception e) {
            Hyperspace.LOGGER.warn("Couldn't switch Iris shaders", e);
            config = null;
        }
    }
}
