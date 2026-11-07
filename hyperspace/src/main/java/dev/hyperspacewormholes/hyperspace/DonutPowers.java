package dev.hyperspacewormholes.hyperspace;

import dev.hyperspacewormholes.wormholes.GameplayConfig;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * The Legends Z-A Flavor Powers past Sparkling and Alpha, decided by the berries instead of rolled at random, so a recipe
 * always gives the same donut. The strongest flavour (a tie gives both) at 120/200/300 points is Lv. 1/2/3: Sweet gives
 * Humungo with 5 or more berries and Teensy with fewer, Sour gives Item Power and Big Haul, Dry gives Encounter and
 * Catching Power. The trip holds them, so everyone through the portal shares them; a Splice has no donut and no powers.
 */
public record DonutPowers(int humungo, int teensy, int sour, int dry) {
    public static final DonutPowers NONE = new DonutPowers(0, 0, 0, 0);

    private static final int[] SCORES = {120, 200, 300};
    private static final int BIG_DONUT = 5;
    private static final int[] HUMUNGO_FROM = {128, 160, 224};
    private static final int[] TEENSY_TO = {127, 95, 31};
    private static final float[] ITEM_CHANCE = {0f, 0.25f, 0.5f, 1f};
    private static final int[] BIG_HAUL = {0, 2, 3, 5};
    private static final int[] ENCOUNTER_EXTRA = {0, 3, 6, 9};
    private static final float[] CATCHING = {0f, 0.1f, 0.2f, 0.3f};

    public static void register() {
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe(Priority.HIGH, event -> {
            if (event.getCaptureResult().isSuccessfulCapture() || !(event.getThrower() instanceof ServerPlayer player)) return Unit.INSTANCE;
            if (!player.level().dimension().equals(Hyperspace.HYPERSPACE)) return Unit.INSTANCE;
            WorldData.Trip trip = WorldData.get(player.level().getServer()).trips.get(player.getUUID());
            if (trip != null && player.getRandom().nextFloat() < CATCHING[trip.powers.dry] * (float) GameplayConfig.get().catchingPowerMultiplier) event.setCaptureResult(CaptureContext.Companion.successful(false));
            return Unit.INSTANCE;
        });
    }

    public static DonutPowers of(List<String> ids) {
        int[] total = DonutBoosts.flavours(ids);
        int top = 0;
        for (int value : total) top = Math.max(top, value);
        int sweet = level(total[0], top);
        boolean big = ids.size() >= BIG_DONUT;
        return new DonutPowers(big ? sweet : 0, big ? 0 : sweet, level(total[2], top), level(total[4], top));
    }

    private static int level(int value, int top) {
        if (value < top) return 0;
        int level = 0;
        for (int needed : SCORES) if (value >= needed) level++;
        return level;
    }

    public void describe(List<String> powers) {
        if (humungo > 0) powers.add("Humungo Power Lv. " + humungo);
        if (teensy > 0) powers.add("Teensy Power Lv. " + teensy);
        if (sour > 0) {
            powers.add("Item Power Lv. " + sour);
            powers.add("Big Haul Lv. " + sour);
        }
        if (dry > 0) {
            powers.add("Encounter Power Lv. " + dry);
            powers.add("Catching Power Lv. " + dry);
        }
    }

    public void size(Pokemon pokemon, RandomSource random) {
        if (humungo > 0) pokemon.setScaleModifier(strength(scale(HUMUNGO_FROM[humungo - 1], 255, random), GameplayConfig.get().humungoPowerMultiplier));
        else if (teensy > 0) pokemon.setScaleModifier(strength(scale(0, TEENSY_TO[teensy - 1], random), GameplayConfig.get().teensyPowerMultiplier));
    }
    private static float strength(float scale, double multiplier) {
        return Math.max(0.1f, 1 + (scale - 1) * (float) multiplier);
    }

    private static float scale(int from, int to, RandomSource random) {
        return 0.5f + (from + random.nextInt(to - from + 1)) / 255f;
    }

    public float itemChance() {
        return Math.min(1f, ITEM_CHANCE[sour] * (float) GameplayConfig.get().itemPowerMultiplier);
    }

    public int bigHaul() {
        return (int) Math.round(BIG_HAUL[sour] * GameplayConfig.get().bigHaulPowerMultiplier);
    }

    public int nearbyCap(int cap) {
        return cap + (int) Math.round(ENCOUNTER_EXTRA[dry] * GameplayConfig.get().encounterPowerMultiplier);
    }

    public int[] saved() {
        return new int[]{humungo, teensy, sour, dry};
    }

    public static DonutPowers load(int[] saved) {
        return saved.length == 4 ? new DonutPowers(saved[0], saved[1], saved[2], saved[3]) : NONE;
    }
}
