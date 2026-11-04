package dev.hyperspacewormholes.usum.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hyperspacewormholes.usum.CameraShakePayload;
import dev.hyperspacewormholes.usum.ModelMotionPayload;
import dev.hyperspacewormholes.usum.MoveFx;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Plays the camera shakes the server sends for move animations (MoveFx): a small turn of the camera from smooth noise (mixin.CameraShakeMixin) at Ultra Sun's strength times {@value #SHAKE_DEGREES} degrees, full within {@value #SHAKE_NEAR} blocks of the battle and gone at {@value #SHAKE_FAR}. Times run on the level's game time, so they pause with the game.
 * It has a toggle in config/horizons_movefx.json (cameraShake, on by default), written with the default when missing.
 */
public final class MoveFxClient implements ClientModInitializer {
    private static final float SHAKE_DEGREES = 0.035f;
    private static final float SHAKE_RATE = 0.12f;
    private static final double SHAKE_NEAR = 24;
    private static final double SHAKE_FAR = 48;
    private static final List<Shake> SHAKES = new ArrayList<>();
    private static boolean cameraShake = true;

    private record Shake(double start, Vec3 centre, float duration, float from, float to, float rate) {
        boolean over(double now) {
            return (now - start) / 20.0 > duration;
        }
    }

    @Override
    public void onInitializeClient() {
        loadConfig();
        ClientPlayNetworking.registerGlobalReceiver(CameraShakePayload.TYPE, (payload, context) -> {
            double now = now(0);
            if (cameraShake && now >= 0) {
                SHAKES.add(new Shake(now + payload.delay() * 20, payload.centre(), payload.duration(), payload.from(),
                        payload.to(), payload.rate()));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ModelMotionPayload.TYPE, (payload, context) -> ModelMotion.add(payload));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ModelMotion.tick();
            double now = now(0);
            SHAKES.removeIf(shake -> now < 0 || shake.over(now));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            SHAKES.clear();
            ModelMotion.clear();
        });
    }

    private static double now(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? -1 : mc.level.getGameTime() + partialTick;
    }

    public static float @Nullable [] shake(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (SHAKES.isEmpty() || mc.player == null) return null;
        double now = now(partialTick);
        if (now < 0) return null;
        Vec3 eye = mc.player.getEyePosition(partialTick);
        float best = 0;
        float rate = 0;
        for (Shake shake : SHAKES) {
            double t = (now - shake.start()) / 20.0;
            if (t < 0 || t > shake.duration()) continue;
            double distance = eye.distanceTo(shake.centre());
            if (distance > SHAKE_FAR) continue;
            double near = distance < SHAKE_NEAR ? 1 : (SHAKE_FAR - distance) / (SHAKE_FAR - SHAKE_NEAR);
            float amplitude = (float) (Mth.lerp(t / shake.duration(), shake.from(), shake.to()) * near);
            if (amplitude > best) {
                best = amplitude;
                rate = shake.rate();
            }
        }
        if (best <= 0) return null;
        double phase = now * rate * SHAKE_RATE;
        float degrees = best * SHAKE_DEGREES;
        return new float[]{(float) (noise(phase, 11) * degrees), (float) (noise(phase + 17.3, 23) * degrees)};
    }

    private static double noise(double t, long seed) {
        long i = (long) Math.floor(t);
        double f = t - i;
        double a = unit(seed * 31 + i) * 2 - 1;
        double b = unit(seed * 31 + i + 1) * 2 - 1;
        return Mth.lerp(f * f * (3 - 2 * f), a, b);
    }

    private static double unit(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x = x ^ (x >>> 31);
        return (x >>> 11) * 0x1.0p-53;
    }

    private static void loadConfig() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(MoveFx.NAMESPACE + ".json");
        JsonObject json = new JsonObject();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement read = JsonParser.parseReader(reader);
                if (read.isJsonObject()) json = read.getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                json = new JsonObject();
            }
        }
        boolean complete = json.has("cameraShake");
        cameraShake = !complete || json.get("cameraShake").getAsBoolean();
        if (complete) return;
        json.addProperty("cameraShake", cameraShake);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
