package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.wormholes.Wormholes;
import kotlin.Unit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Ultra Ruin's Guzzlord eats: the one met at the zone's spot (WormholeDestinations.spawnWild) is turned away from
 * the arrival and gets the aspect EATING, which the resolver {@code 0799_guzzlord/1_guzzlord_eating.json} maps to the
 * poser {@code cobblemon:guzzlord_eating}: out of battle it loops {@code animation.guzzlord_eating.eat}, the game's own
 * eating motion, and in battle it stands in its model's idle.
 * <p>
 * It notices its player within WAKE of its hitbox and HEIGHT of its feet, or when a battle with it starts or it is hurt,
 * each player only ever waking their own (OwnEncounters). The aspect becomes TURNING (the same poser's stomping on the
 * spot) while it turns to them, eased over TURN_TICKS, then ROARING (that poser's idle), in which it plays
 * {@code animation.guzzlord_roar.roar} from ROAR_TICK, once the blend out of the stomping is over, and cries at the top
 * of it (CRY_TICK). Pose changes blend in the poser; the last one, off it at DONE_TICK, is between two equal idles. From
 * then on it is an ordinary wild one with AI. While it has any of the STAGES a thrown ball is cancelled on hitting it, as
 * a sleeping Xurkitree's is. The stage is saved with it: one still EATING when it loads eats on, one that was turning or
 * roaring is over it. While it turns its body follows it on both sides (GuzzlordTurnMixin).
 * When caught the aspects go, so a caught Guzzlord poses as any other. The poser and animations are built from the
 * player's own ATM x MSD (MsdTransforms); without them it just stands and turns.
 */
public final class RuinGuzzlord {
    private static final String EATING = "hyperspace_eating", TURNING = "hyperspace_turning", ROARING = "hyperspace_roaring";
    private static final Set<String> STAGES = Set.of(EATING, TURNING, ROARING);
    private static final String ROAR = "q.bedrock_primary('guzzlord_roar', 'roar', q.curve('symmetrical'))";
    private static final double WAKE = 15, HEIGHT = 6;
    private static final int TURN_TICKS = 26, STAND_TICK = 20, ROAR_TICK = 29, CRY_TICK = ROAR_TICK + 16;
    private static final int DONE_TICK = ROAR_TICK + 49;

    private static final class Meal {
        int age = -1;
        float from, turn;
    }

    private static final Map<PokemonEntity, Meal> MEALS = new HashMap<>();

    private RuinGuzzlord() {}

    public static void register() {
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.HIGHEST, event -> {
            if (busy(event.getPokemon())) event.cancel();
            return Unit.INSTANCE;
        });
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.HIGHEST, event -> {
            stage(event.getPokemon(), null);
            return Unit.INSTANCE;
        });
        Wormholes.onEntityLoad(RuinGuzzlord::onLoad);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> MEALS.clear());
    }

    static boolean eats(Level level, PokemonEntity entity) {
        return level.dimension() == Wormholes.ULTRA_RUIN
                && entity.getPokemon().getSpecies().getResourceIdentifier().getPath().equals("guzzlord");
    }

    static void eat(PokemonEntity entity) {
        stage(entity, EATING);
    }

    static boolean met(Entity entity) {
        return entity instanceof PokemonEntity pokemon && eats(entity.level(), pokemon) && WormholeDestinations.held(entity);
    }

    public static boolean turns(PokemonEntity entity) {
        Set<String> aspects = entity.getEntityData().get(PokemonEntity.getASPECTS());
        return aspects.contains(TURNING) || aspects.contains(ROARING);
    }

    private static boolean busy(PokemonEntity entity) {
        return entity.getPokemon().getForcedAspects().stream().anyMatch(STAGES::contains);
    }

    private static void stage(PokemonEntity entity, @Nullable String stage) {
        stage(entity.getPokemon(), stage);
        entity.getEntityData().set(PokemonEntity.getASPECTS(), entity.getPokemon().getAspects());
    }

    private static void stage(Pokemon pokemon, @Nullable String stage) {
        Set<String> forced = new HashSet<>(pokemon.getForcedAspects());
        boolean changed = forced.removeAll(STAGES);
        if (stage != null) changed |= forced.add(stage);
        if (!changed) return;
        pokemon.setForcedAspects(forced);
        pokemon.updateAspects();
    }

    private static void onLoad(Entity entity, ServerLevel level) {
        if (!(entity instanceof PokemonEntity pokemon) || !eats(level, pokemon) || !busy(pokemon)) return;
        if (!pokemon.getPokemon().getForcedAspects().contains(EATING)) {
            calm(pokemon);
            return;
        }
        stage(pokemon, EATING);
        face(pokemon, pokemon.getYRot());
        MEALS.putIfAbsent(pokemon, new Meal());
    }

    private static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<PokemonEntity, Meal>> it = MEALS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<PokemonEntity, Meal> m = it.next();
            PokemonEntity e = m.getKey();
            Meal meal = m.getValue();
            if (!e.isAlive() || !busy(e) || !(e.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            Player player = OwnEncounters.nearest(e, level, e.position(), -1);
            if (meal.age < 0) {
                if (!near(e, player) && !e.isBattling() && e.hurtTime == 0) continue;
                meal.from = e.getYRot();
                stage(e, TURNING);
            }
            meal.age++;
            if (meal.age <= TURN_TICKS) turn(e, meal, player);
            if (meal.age == STAND_TICK) stage(e, ROARING);
            if (meal.age == ROAR_TICK) e.playAnimation(ROAR, List.of());
            if (meal.age == CRY_TICK) WormholeSounds.cry(e);
            if (meal.age < DONE_TICK) continue;
            calm(e);
            it.remove();
        }
    }

    private static boolean near(PokemonEntity entity, @Nullable Player player) {
        return player != null && Math.abs(player.getY() - entity.getY()) < HEIGHT
                && entity.getBoundingBox().distanceToSqr(player.position()) < WAKE * WAKE;
    }

    /** The turn is carried on from tick to tick, so a player crossing behind it never flips which way it goes round. */
    private static void turn(PokemonEntity entity, Meal meal, @Nullable Player player) {
        if (player != null) {
            meal.turn += Mth.wrapDegrees(WildsShadows.facing(entity.position(), player.position()) - meal.from - meal.turn);
        }
        float t = meal.age / (float) TURN_TICKS;
        face(entity, Mth.wrapDegrees(meal.from + meal.turn * t * t * (3 - 2 * t)));
    }

    private static void face(PokemonEntity entity, float yaw) {
        entity.setYRot(yaw);
        entity.setYBodyRot(yaw);
        entity.setYHeadRot(yaw);
        entity.getEntityData().set(PokemonEntity.getSPAWN_DIRECTION(), Mth.wrapDegrees(yaw));
    }

    private static void calm(PokemonEntity entity) {
        stage(entity, null);
        face(entity, entity.getYRot());
        entity.setNoAi(false);
    }
}
