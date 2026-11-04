package dev.hyperspacewormholes.usum.battle;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import kotlin.Unit;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns bosses in their fights. A boss fought in several battles at once (tag {@value #BOSS}) is turned once a tick at
 * {@value #TURN_SPEED} degrees, not once per battle, so it doesn't jerk between trainers: toward the Pokemon it is attacking
 * (the wormholes part's Bosses.target, found by reflection so neither part needs the other), else the middle of its foes.
 * Ridden or riding bosses, the Megalopolis Necrozma and stays-put Pokemon are left as they are.
 */
public final class BattleSpacing implements ModInitializer {
    private static final String NECROZMA_BOSS = "hyperspace_wormholes.necrozma_boss";
    private static final String BOSS = "hyperspace_wormholes.boss";
    private static final String STAYS_PUT = "hyperspace_wormholes.stays_put";
    private static final float TURN_SPEED = 14f;

    private static final List<PokemonBattle> BATTLES = new ArrayList<>();

    private static @Nullable Method targetMethod;
    private static boolean targetLooked;

    @Override
    public void onInitialize() {
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(Priority.NORMAL, event -> {
            BATTLES.add(event.getBattle());
            return Unit.INSTANCE;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
    }

    private static void tick() {
        BATTLES.removeIf(PokemonBattle::getEnded);
        faceBosses();
    }

    private static boolean boss(PokemonEntity entity) {
        return entity.getTags().contains(BOSS);
    }

    private static boolean pinned(PokemonEntity entity) {
        Set<String> tags = entity.getTags();
        return tags.contains(NECROZMA_BOSS) || tags.contains(STAYS_PUT);
    }

    private static boolean busy(PokemonEntity entity) {
        return entity.isVehicle() || entity.isPassenger();
    }

    private static void faceBosses() {
        Map<PokemonEntity, List<PokemonEntity>> foes = new HashMap<>();
        for (PokemonBattle battle : BATTLES) {
            List<PokemonEntity> one = onField(battle.getSide1()), two = onField(battle.getSide2());
            for (PokemonEntity entity : one) {
                if (boss(entity)) foes.computeIfAbsent(entity, e -> new ArrayList<>()).addAll(two);
            }
            for (PokemonEntity entity : two) {
                if (boss(entity)) foes.computeIfAbsent(entity, e -> new ArrayList<>()).addAll(one);
            }
        }
        for (Map.Entry<PokemonEntity, List<PokemonEntity>> entry : foes.entrySet()) {
            PokemonEntity boss = entry.getKey();
            List<PokemonEntity> around = new ArrayList<>();
            for (PokemonEntity foe : entry.getValue()) {
                if (foe.level() == boss.level() && !around.contains(foe)) around.add(foe);
            }
            if (around.isEmpty()) continue;
            Entity target = bossTarget(boss);
            face(boss, target != null && target.level() == boss.level() ? target.position() : centre(around));
        }
    }

    private static @Nullable Entity bossTarget(PokemonEntity boss) {
        if (!targetLooked) {
            targetLooked = true;
            try {
                targetMethod = Class.forName("dev.hyperspacewormholes.wormholes.boss.Bosses").getMethod("target", Entity.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                targetMethod = null;
            }
        }
        if (targetMethod == null) return null;
        try {
            return targetMethod.invoke(null, boss) instanceof Entity target && !target.isRemoved() ? target : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static List<PokemonEntity> onField(BattleSide side) {
        List<PokemonEntity> out = new ArrayList<>();
        for (BattleActor actor : side.getActors()) {
            for (ActiveBattlePokemon active : actor.getActivePokemon()) {
                BattlePokemon pokemon = active.getBattlePokemon();
                PokemonEntity entity = pokemon == null ? null : pokemon.getEntity();
                if (entity != null && !entity.isRemoved()) out.add(entity);
            }
        }
        return out;
    }

    private static Vec3 centre(List<PokemonEntity> side) {
        Vec3 sum = Vec3.ZERO;
        for (PokemonEntity entity : side) sum = sum.add(entity.position());
        return sum.scale(1.0 / side.size());
    }

    private static void face(PokemonEntity entity, Vec3 at) {
        if (busy(entity) || pinned(entity)) return;
        double dx = at.x - entity.getX(), dz = at.z - entity.getZ();
        if (dx * dx + dz * dz < 1e-4) return;
        float wanted = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float yaw = Mth.approachDegrees(entity.getYRot(), wanted, TURN_SPEED);
        entity.setYRot(yaw);
        entity.setYBodyRot(yaw);
        entity.setYHeadRot(yaw);
    }
}
