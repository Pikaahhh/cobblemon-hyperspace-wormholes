package dev.hyperspacewormholes.wormholes;

import com.cobblemon.mod.common.Cobblemon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.hyperspacewormholes.Packets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * The gameplay settings, config/hyperspace_wormholes_gameplay.json (written on first start), also shown under Config in the mods list.
 * Every default matches how the mod plays without the file. Pity of 0 turns that pity off. The ring lists run no rings,
 * one, two, top tier. wormholeRingWeights multiply the odds of each ring count. wormholeShinyOneIn is the shiny chance
 * of a regular wormhole Pokemon as "1 in N" for no rings up to three, the ring ones as they are after 5,000 light-years
 * (shorter rides get less, WormholePokemon.shinyChance); 0 is never. Files from before 2.1.0 had a base percent and a
 * multiplier per ring instead, which are turned into the four values once (migrate) and then left out of the file.
 * wormholeLegendaryShinyOneIn and wormholeUltraBeastShinyOneIn are the "1 in N" shiny odds of legendaries (mythicals
 * and paradox Pokemon too) and Ultra Beasts past wormholes and from the Item Analyser, hyperspaceLegendaryShinyOneIn
 * Hyperspace's legendary bosses; 0, the default, is Cobblemon's own shiny rate (specialShinyRate).
 * distortionStarWeights are relative weights for 1 to 5 stars. A power multiplier of 1 is normal
 * and 0 turns that power off. maxDistortionsPerPlayer is how many a player keeps around them where the world has most,
 * half that in the quieter areas. distortionMinutes is how long a distortion stays. distortionsAvoidBases keeps new ones
 * distortionBaseDistance blocks from online players' respawn points and out of chunks players have spent an hour around. The legendary switches turn off each way of getting a legendary: Hyperspace's legendary
 * spawns, legendaries past wormholes, catching a beaten boss, and the Item Analyser's Cosmog. legendaryMonuments is MONUMENTS (Legendary Monuments'
 * own legendaries are left out of Hyperspace when it is installed) or BOTH. A server's values go to its players (Sync),
 * because the wormhole tunnel is worked out on both sides and has to match.
 */
public final class GameplayConfig {
    public static final int RING_TIERS = 4, STARS = 5, MAX_DISTORTIONS = 16, MAX_DISTORTION_MINUTES = 10080, MAX_BASE_DISTANCE = 1000;
    public static final String MONUMENTS = "monuments", BOTH = "both";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = Wormholes.MOD_ID + "_gameplay.json";
    private static final double MOST = 1000, MOST_ONE_IN = 1_000_000;
    /** How far past the no-ring chance the old multiplier carried each ring count at 5,000 light-years, in percent. */
    private static final double[] LEGACY_RING_PERCENT = {0, 9, 18, 35};
    private static GameplayConfig instance = new GameplayConfig();
    private static volatile GameplayConfig synced;

    public int hoopaPity = 12;
    public int necrozmaPity = 12;
    public ShinyOdds wormholeShinyOneIn = new ShinyOdds();
    public Double wormholeBaseShinyChance;
    public Double wormholeShinyMultiplierPerRing;
    public double[] wormholeRingWeights = {1, 1, 1, 1};
    public double wormholeLegendaryShinyOneIn = 0;
    public double wormholeUltraBeastShinyOneIn = 0;
    public double hyperspaceLegendaryShinyOneIn = 0;
    public double sparklingPowerMultiplier = 1;
    public double alphaPowerMultiplier = 1;
    public double humungoPowerMultiplier = 1;
    public double teensyPowerMultiplier = 1;
    public double itemPowerMultiplier = 1;
    public double bigHaulPowerMultiplier = 1;
    public double encounterPowerMultiplier = 1;
    public double catchingPowerMultiplier = 1;
    public int maxDistortionsPerPlayer = 2;
    public int distortionMinutes = 60;
    public boolean distortionsAvoidBases = false;
    public int distortionBaseDistance = 128;
    public int[] distortionStarWeights = {35, 28, 20, 12, 5};
    public boolean bossHardModeAlwaysOn = false;
    public boolean monitorBreaksFromLightning = true;
    public boolean monitorBreaksFromElectricMoves = true;
    public boolean monitorBreaksFromOtherMoves = true;
    public boolean hyperspaceLegendaries = true;
    public boolean wormholeLegendaries = true;
    public boolean bossCatches = true;
    public boolean cosmogSummon = true;
    public String legendaryMonuments = MONUMENTS;

    /** "1 in N" for each ring count; the defaults round the game's chances at 5,000 light-years (1%, 10%, 19%, 36%) to 1 in 100, 10, 5 and 3. */
    public static final class ShinyOdds {
        public double noRings = 100;
        public double oneRing = 10;
        public double twoRings = 5;
        public double threeRings = 3;

        public double get(int rings) {
            return switch (Math.min(rings, RING_TIERS - 1)) {
                case 0 -> noRings;
                case 1 -> oneRing;
                case 2 -> twoRings;
                default -> threeRings;
            };
        }

        public void set(int rings, double oneIn) {
            switch (Math.min(rings, RING_TIERS - 1)) {
                case 0 -> noRings = oneIn;
                case 1 -> oneRing = oneIn;
                case 2 -> twoRings = oneIn;
                default -> threeRings = oneIn;
            }
        }

        /** The chance from 0 to 1: 0 is never, and 1 or less is always. */
        public double chance(int rings) {
            double oneIn = get(rings);
            return oneIn <= 0 ? 0 : Math.min(1, 1 / oneIn);
        }

        public ShinyOdds copy() {
            ShinyOdds to = new ShinyOdds();
            for (int i = 0; i < RING_TIERS; i++) to.set(i, get(i));
            return to;
        }
    }

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
        Packets.toClient(Sync.TYPE, Sync.CODEC);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && player.connection.hasChannel(Sync.TYPE)) PacketDistributor.sendToPlayer(player, new Sync(GSON.toJson(instance)));
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
        return FMLPaths.CONFIGDIR.get().resolve(FILE);
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
        if (wormholeShinyOneIn == null) wormholeShinyOneIn = fresh.wormholeShinyOneIn;
        if (wormholeBaseShinyChance != null || wormholeShinyMultiplierPerRing != null) migrate();
        for (int i = 0; i < RING_TIERS; i++) wormholeShinyOneIn.set(i, oneIn(wormholeShinyOneIn.get(i), fresh.wormholeShinyOneIn.get(i)));
        wormholeRingWeights = list(wormholeRingWeights, fresh.wormholeRingWeights);
        if (Arrays.stream(wormholeRingWeights).sum() <= 0) wormholeRingWeights = fresh.wormholeRingWeights;
        wormholeLegendaryShinyOneIn = specialOneIn(wormholeLegendaryShinyOneIn);
        wormholeUltraBeastShinyOneIn = specialOneIn(wormholeUltraBeastShinyOneIn);
        hyperspaceLegendaryShinyOneIn = specialOneIn(hyperspaceLegendaryShinyOneIn);
        sparklingPowerMultiplier = number(sparklingPowerMultiplier);
        alphaPowerMultiplier = number(alphaPowerMultiplier);
        humungoPowerMultiplier = number(humungoPowerMultiplier);
        teensyPowerMultiplier = number(teensyPowerMultiplier);
        itemPowerMultiplier = number(itemPowerMultiplier);
        bigHaulPowerMultiplier = number(bigHaulPowerMultiplier);
        encounterPowerMultiplier = number(encounterPowerMultiplier);
        catchingPowerMultiplier = number(catchingPowerMultiplier);
        maxDistortionsPerPlayer = Math.max(0, Math.min(MAX_DISTORTIONS, maxDistortionsPerPlayer));
        distortionMinutes = Math.max(1, Math.min(MAX_DISTORTION_MINUTES, distortionMinutes));
        distortionBaseDistance = Math.max(0, Math.min(MAX_BASE_DISTANCE, distortionBaseDistance));
        if (distortionStarWeights == null || distortionStarWeights.length != STARS || Arrays.stream(distortionStarWeights).anyMatch(w -> w < 0)
                || Arrays.stream(distortionStarWeights).sum() <= 0) distortionStarWeights = fresh.distortionStarWeights;
        if (!BOTH.equals(legendaryMonuments)) legendaryMonuments = MONUMENTS;
    }

    /** A file from before 2.1.0: its base percent and multiplier give each ring count's chance at 5,000 light-years. One left
     *  at the old defaults (1% and 1) takes the new rounded defaults instead. */
    private void migrate() {
        double base = wormholeBaseShinyChance == null ? 1 : Math.min(100, number(wormholeBaseShinyChance));
        double perRing = wormholeShinyMultiplierPerRing == null ? 1 : number(wormholeShinyMultiplierPerRing);
        wormholeBaseShinyChance = null;
        wormholeShinyMultiplierPerRing = null;
        if (base == 1 && perRing == 1) return;
        for (int i = 0; i < RING_TIERS; i++) {
            double percent = Math.min(100, base + LEGACY_RING_PERCENT[i] * perRing);
            wormholeShinyOneIn.set(i, percent <= 0 ? 0 : 100 / percent);
        }
        Wormholes.LOGGER.info("Shiny settings from before 2.1.0 ({}% base, x{} per ring) are now 1 in {}, {}, {} and {}", base, perRing,
                wormholeShinyOneIn.noRings, wormholeShinyOneIn.oneRing, wormholeShinyOneIn.twoRings, wormholeShinyOneIn.threeRings);
    }

    private static double oneIn(double value, double fresh) {
        if (!Double.isFinite(value)) return fresh;
        return value <= 0 ? 0 : Math.max(1, Math.min(MOST_ONE_IN, value));
    }

    /** A legendary or Ultra Beast "1 in N": 0 (or anything below 0 or not a number) is Cobblemon's own rate. */
    private static double specialOneIn(double value) {
        return !Double.isFinite(value) || value <= 0 ? 0 : Math.max(1, Math.min(MOST_ONE_IN, value));
    }

    /** The "1 in N" a legendary or Ultra Beast setting rolls at: its own value, or Cobblemon's shiny rate when it's 0. */
    public static float specialShinyRate(double oneIn) {
        return oneIn > 0 ? (float) oneIn : Math.max(1f, Cobblemon.INSTANCE.getConfig().getShinyRate());
    }

    public boolean leaveToMonuments() {
        return !BOTH.equals(legendaryMonuments);
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
