package dev.hyperspacewormholes.wormholes.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hyperspacewormholes.wormholes.network.RoarRequestPayload;
import dev.hyperspacewormholes.wormholes.ride.LegendRoar;
import dev.hyperspacewormholes.wormholes.ride.UltraWarpRules;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * The legend's roar on the client: the key that asks for it, and keeping track of which legends are mid-roar so they
 * animate.
 */
public final class RoarClient {
    private static final Map<Integer, Long> ROARING_SINCE = new HashMap<>();
    private static KeyMapping key;

    private RoarClient() {}

    static void registerKey() {
        key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hyperspace_wormholes.roar",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.hyperspace_wormholes"));
    }

    static void tick(Minecraft mc) {
        while (key.consumeClick()) {
            if (mc.player != null && mc.player.getVehicle() != null && !UltraWarpRules.inWarp(mc.player)) {
                ClientPlayNetworking.send(RoarRequestPayload.INSTANCE);
            }
        }
        if (mc.level == null) ROARING_SINCE.clear();
        else ROARING_SINCE.values().removeIf(since -> mc.level.getGameTime() - since > LegendRoar.ROAR_TICKS);
    }

    static void startRoar(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) ROARING_SINCE.put(entityId, mc.level.getGameTime());
    }

    public static float roarAmount(Entity entity, float partialTick) {
        Long since = ROARING_SINCE.get(entity.getId());
        if (since == null) return 0f;
        float t = entity.level().getGameTime() - since + partialTick;
        if (t < 0 || t > LegendRoar.ROAR_TICKS) return 0f;
        float amount = Math.min(Math.min(1f, t / 6f), (LegendRoar.ROAR_TICKS - t) / 8f);
        return amount * amount * (3 - 2 * amount);
    }
}
