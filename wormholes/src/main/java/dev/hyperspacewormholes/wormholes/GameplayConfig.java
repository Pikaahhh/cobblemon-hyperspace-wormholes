package dev.hyperspacewormholes.wormholes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * The gameplay settings, config/hyperspace_wormholes_gameplay.json (written on first start), also shown in Mod Menu.
 * Every default matches how the mod plays without the file. Pity of 0 turns that pity off. The ring lists run no rings,
 * one, two, top tier. wormholeRingWeights multiply the odds of each ring count. wormholeBaseShinyChance is the percent
 * chance with no rings and wormholeShinyMultiplierPerRing scales what rings add on top. distortionStarWeights are relative weights for 1 to 5 stars. A power multiplier of 1 is normal
 * and 0 turns that power off. maxDistortionsPerPlayer is how many a player keeps around them where the world has most,
 * half that in the quieter areas. A server's values go to its players (Sync), because the wormhole tunnel is worked out
 * on both sides and has to match.
 */
public final class GameplayConfig {
    public static final int RING_TIERS = 4, STARS = 5, MAX_DISTORTIONS = 16;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = Wormholes.MOD_ID + "_gameplay.json";
    private static final double MOST = 1000;
    private static GameplayConfig instance = new GameplayConfig();
    private static volatile GameplayConfig synced;

    public int hoopaPity = 12;
    public int necrozmaPity = 12;
    public double wormholeBaseShinyChance = 1;
    public double wormholeShinyMultiplierPerRing = 1;
    public double[] wormholeRingWeights = {1, 1, 1, 1};
    public double sparklingPowerMultiplier = 1;
    public double alphaPowerMultiplier = 1;
    public double humungoPowerMultiplier = 1;
    public double teensyPowerMultiplier = 1;
    public double itemPowerMultiplier = 1;
    public double bigHaulPowerMultiplier = 1;
    public double encounterPowerMultiplier = 1;
    public double catchingPowerMultiplier = 1;
    public int maxDistortionsPerPlayer = 2;
    public int[] distortionStarWeights = {35, 28, 20, 12, 5};
    public boolean bossHardModeAlwaysOn = false;
    public boolean monitorBreaksFromLightning = true;
    public boolean monitorBreaksFromElectricMoves = true;
    public boolean monitorBreaksFromOtherMoves = true;

    public record Sync(String json) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wormholes.MOD_ID, "gameplay_config"));
        public static final StreamCodec<FriendlyByteBuf, Sync> CODEC =
                StreamCodec.of((buf, sync) -> buf.writeUtf(sync.json(), Short.MAX_VALUE), buf -> new Sync(buf.readUtf(Short.MAX_VALUE)));

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }

    public static GameplayConfig get() {
        GameplayConfig from = synced;
        return from != null ? from : instance;
    }

    public static GameplayConfig local() {
        return instance;
    }

    public static void register() {
        load();
        PayloadTypeRegistry.playS2C().register(Sync.TYPE, Sync.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (ServerPlayNetworking.canSend(handler.getPlayer(), Sync.TYPE)) ServerPlayNetworking.send(handler.getPlayer(), new Sync(GSON.toJson(instance)));
        });
    }

    public static void receive(Sync sync) {
        try {
            GameplayConfig read = GSON.fromJson(sync.json(), GameplayConfig.class);
            if (read != null) {
                read.tidy();
                synced = read;
            }
        } catch (RuntimeException e) {
            Wormholes.LOGGER.error("Couldn't read the server's gameplay settings", e);
        }
    }

    public static void forgetServer() {
        synced = null;
    }

    public static GameplayConfig defaults() {
        return new GameplayConfig();
    }

    public static void save(GameplayConfig edited, boolean ownWorld) {
        edited.tidy();
        instance = edited;
        if (ownWorld) synced = null;
        write();
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE);
    }

    private static void load() {
        Path path = path();
        try {
            if (Files.exists(path)) {
                GameplayConfig loaded = GSON.fromJson(Files.readString(path), GameplayConfig.class);
                if (loaded != null) instance = loaded;
            }
            instance.tidy();
        } catch (IOException | RuntimeException e) {
            Wormholes.LOGGER.error("Failed to read {}, using defaults", path, e);
            instance = new GameplayConfig();
        }
        write();
    }

    private static void write() {
        Path path = path();
        try {
            Files.writeString(path, GSON.toJson(instance));
        } catch (IOException e) {
            Wormholes.LOGGER.error("Failed to write {}", path, e);
        }
    }

    private void tidy() {
        GameplayConfig fresh = new GameplayConfig();
        hoopaPity = Math.max(0, hoopaPity);
        necrozmaPity = Math.max(0, necrozmaPity);
        wormholeBaseShinyChance = Math.min(100, number(wormholeBaseShinyChance));
        wormholeShinyMultiplierPerRing = number(wormholeShinyMultiplierPerRing);
        wormholeRingWeights = list(wormholeRingWeights, fresh.wormholeRingWeights);
        if (Arrays.stream(wormholeRingWeights).sum() <= 0) wormholeRingWeights = fresh.wormholeRingWeights;
        sparklingPowerMultiplier = number(sparklingPowerMultiplier);
        alphaPowerMultiplier = number(alphaPowerMultiplier);
        humungoPowerMultiplier = number(humungoPowerMultiplier);
        teensyPowerMultiplier = number(teensyPowerMultiplier);
        itemPowerMultiplier = number(itemPowerMultiplier);
        bigHaulPowerMultiplier = number(bigHaulPowerMultiplier);
        encounterPowerMultiplier = number(encounterPowerMultiplier);
        catchingPowerMultiplier = number(catchingPowerMultiplier);
        maxDistortionsPerPlayer = Math.max(0, Math.min(MAX_DISTORTIONS, maxDistortionsPerPlayer));
        if (distortionStarWeights == null || distortionStarWeights.length != STARS || Arrays.stream(distortionStarWeights).anyMatch(w -> w < 0)
                || Arrays.stream(distortionStarWeights).sum() <= 0) distortionStarWeights = fresh.distortionStarWeights;
    }

    private static double[] list(double[] read, double[] fresh) {
        if (read == null || read.length != RING_TIERS) return fresh;
        double[] out = new double[RING_TIERS];
        for (int i = 0; i < RING_TIERS; i++) out[i] = number(read[i]);
        return out;
    }

    private static double number(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(MOST, value)) : 1;
    }

    public double ringWeight(int rings) {
        return wormholeRingWeights[Math.min(rings, RING_TIERS - 1)];
    }
}
