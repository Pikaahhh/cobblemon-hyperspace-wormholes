package dev.hyperspacewormholes.wormholes.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hyperspacewormholes.wormholes.network.RoarRequestPayload;

import dev.hyperspacewormholes.wormholes.ride.LegendRoar;
import dev.hyperspacewormholes.wormholes.ride.UltraWarpRules;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public final class RoarClient {
    private static final Map<Integer, Long> ROARING_SINCE = new HashMap<>();
    private static KeyMapping key;
    private static KeyMapping waystoneKey;

    private RoarClient() {}

    static void registerKey(IEventBus modBus) {
        key = new KeyMapping("key.hyperspace_wormholes.roar",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.hyperspace_wormholes");
        waystoneKey = new KeyMapping("key.hyperspace_wormholes.waystone_roar",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_COMMA, "key.categories.hyperspace_wormholes");
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(key);
            event.register(waystoneKey);
        });
    }

    static void tick(Minecraft mc) {
        while (key.consumeClick()) request(mc, false);
        while (waystoneKey.consumeClick()) request(mc, true);
        if (mc.level == null) ROARING_SINCE.clear();
        else ROARING_SINCE.values().removeIf(since -> mc.level.getGameTime() - since > LegendRoar.ROAR_TICKS);
    }

    private static void request(Minecraft mc, boolean waystones) {
        if (mc.player != null && mc.player.getVehicle() != null && !UltraWarpRules.inWarp(mc.player)) {
            PacketDistributor.sendToServer(new RoarRequestPayload(waystones));
        }
    }

    static void startRoar(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) ROARING_SINCE.put(entityId, mc.level.getGameTime());
    }

    public static boolean anyRoaring() {
        return !ROARING_SINCE.isEmpty();
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
