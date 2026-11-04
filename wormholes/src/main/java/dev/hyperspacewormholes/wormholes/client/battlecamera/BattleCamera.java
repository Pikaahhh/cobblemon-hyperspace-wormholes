package dev.hyperspacewormholes.wormholes.client.battlecamera;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.client.BossClient;
import dev.hyperspacewormholes.wormholes.client.BurnsSkyClient;
import dev.hyperspacewormholes.wormholes.client.cinematic.UltraBurstCinematic;
import dev.hyperspacewormholes.wormholes.zmove.BurnsSkyTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The wide shot of boss fights (ArenaShot) and nothing else. Any far battler drawn by a boss entity (BossClient's link: Necrozma, Hoopa Unbound, the legendaries Hoopa summons, which LegendWave links the same way) starts the shot, and every other battle keeps the normal view.
 * While a shot is on and the battle isn't minimised the view leaves the player's eyes for it, stepping aside while Ultra Burst cutscene plays.
 * Other entities, the player included, aren't drawn within 3.5 blocks of the camera (4 to come back; more at narrow fields of view, up to 2.5 times at 28 degrees and below) or when they stand between it and the battlers, except Pokemon, which are only hidden up close. Blocks are always drawn.
 */
public final class BattleCamera {
    private static final double MAX_PITCH = 89, CULL_FOV = 70, CULL_GROW = 2.5, CULL_RADIUS = 3.5, CULL_KEEP = 4.0;
    private static final double BALL_OUTER = 1.08;
    private static final Logger LOG = LoggerFactory.getLogger("horizons_movefx camera");
    private static final Map<UUID, Entity> ENTITIES = new HashMap<>();
    private static final Set<Integer> BATTLER_IDS = new HashSet<>();
    private static final Set<UUID> LINKED = new HashSet<>();
    private static final List<Entity> ARENA = new ArrayList<>();
    private static final List<Vec3> SUBJECTS = new ArrayList<>();
    private static final List<UUID> near = new ArrayList<>(), far = new ArrayList<>();
    private static Set<Integer> hiddenBefore = new HashSet<>(), hiddenNow = new HashSet<>();

    private static double clock;
    private static long lastNanos;
    private static @Nullable UUID battleId;
    private static boolean active;
    private static float lastYaw;
    private static @Nullable Shot lastShot;

    public record Shot(Vec3 position, float yaw, float pitch, float roll, float fov) {}

    record Pose(Vec3 pos, Vec3 look, Vec3 up, double fov) {}

    private BattleCamera() {}

    public static boolean active() {
        return active;
    }

    private static double now() {
        long nanos = System.nanoTime();
        if (lastNanos != 0 && active && !Minecraft.getInstance().isPaused()) clock += Math.min((nanos - lastNanos) / 5.0e7, 5);
        lastNanos = nanos;
        return clock;
    }

    static void tick(Minecraft mc) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        double now = now();
        if (battle == null || mc.player == null || mc.level == null) {
            reset();
            active = false;
            return;
        }
        if (!battle.getBattleId().equals(battleId)) {
            reset();
            battleId = battle.getBattleId();
        }
        collect(mc, battle);
        Map<Integer, BurnsSkyClient.Run> runs = BurnsSkyClient.runs();
        List<Entity> nearEntities = new ArrayList<>();
        for (UUID id : near) if (ENTITIES.get(id) != null) nearEntities.add(ENTITIES.get(id));
        ArenaShot.tick(mc, ARENA, nearEntities, !runs.isEmpty(), arenaBall(mc, runs), now);
        active = BattleCameraClient.enabled() && ArenaShot.on() && !battle.getMinimised() && mc.getCameraEntity() == mc.player
                && !mc.player.isPassenger() && !UltraBurstCinematic.isPlaying();
    }

    private static void reset() {
        battleId = null;
        near.clear();
        far.clear();
        ENTITIES.clear();
        BATTLER_IDS.clear();
        LINKED.clear();
        ARENA.clear();
        ArenaShot.clear();
    }

    private static void collect(Minecraft mc, ClientBattle battle) {
        ClientBattleActor own = battle.getParticipatingActor(mc.player.getUUID());
        ClientBattleSide nearSide = own != null ? own.getSide() : battle.getSide1();
        near.clear();
        far.clear();
        for (ClientBattleSide side : battle.getSides()) {
            for (ClientBattleActor actor : side.getActors()) {
                for (ActiveClientBattlePokemon active : actor.getActivePokemon()) {
                    ClientBattlePokemon pokemon = active.getBattlePokemon();
                    if (pokemon != null) (side == nearSide ? near : far).add(pokemon.getUuid());
                }
            }
        }
        ENTITIES.clear();
        BATTLER_IDS.clear();
        ARENA.clear();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof PokemonEntity pokemon && !entity.isRemoved()) {
                UUID id = pokemon.getPokemon().getUuid();
                if (near.contains(id) || far.contains(id)) {
                    ENTITIES.put(id, entity);
                    BATTLER_IDS.add(entity.getId());
                }
            }
        }
        for (UUID id : far) {
            if (ENTITIES.containsKey(id)) continue;
            Entity boss = BossClient.entity(id);
            if (boss == null || boss.isRemoved()) continue;
            ENTITIES.put(id, boss);
            BATTLER_IDS.add(boss.getId());
            if (!ARENA.contains(boss)) ARENA.add(boss);
            if (LINKED.add(id)) LOG.info("[battlecam] battler {} drawn by boss entity {} (Horizons' BossClient)", id, boss.getId());
        }
    }

    private static @Nullable AABB arenaBall(Minecraft mc, Map<Integer, BurnsSkyClient.Run> runs) {
        if (ARENA.isEmpty() || runs.isEmpty()) return null;
        for (BurnsSkyClient.Run run : List.copyOf(runs.values())) {
            if (!ARENA.contains(mc.level.getEntity(run.payload.userId()))) continue;
            double age = mc.level.getGameTime() - run.start;
            Vec3 centre = run.ball(mc, age, 1.0f);
            double radius = BurnsSkyTimeline.ballRadius(age) * BALL_OUTER;
            if (radius > 0) return new AABB(centre, centre).inflate(radius);
        }
        return null;
    }

    public static @Nullable Shot lastShot() {
        return lastShot;
    }

    public static @Nullable Shot frame(float partialTick) {
        Set<Integer> swap = hiddenBefore;
        hiddenBefore = hiddenNow;
        hiddenNow = swap;
        hiddenNow.clear();
        Minecraft mc = Minecraft.getInstance();
        Pose pose = !active || mc.player == null || mc.level == null || UltraBurstCinematic.isPlaying() ? null : ArenaShot.pose(now());
        lastShot = pose == null ? null : shot(pose);
        return lastShot;
    }

    private static Shot shot(Pose pose) {
        Vec3 forward = pose.look().subtract(pose.pos()).normalize();
        SUBJECTS.clear();
        SUBJECTS.add(pose.look());
        for (Entity entity : ENTITIES.values()) SUBJECTS.add(entity.getBoundingBox().getCenter());
        double flat = Math.sqrt(forward.x * forward.x + forward.z * forward.z);
        float yaw = flat < 1e-4 ? lastYaw : (float) Math.toDegrees(Mth.atan2(-forward.x, forward.z));
        lastYaw = yaw;
        float pitch = (float) Mth.clamp(-Math.toDegrees(Math.asin(Mth.clamp(forward.y, -1, 1))), -MAX_PITCH, MAX_PITCH);
        return new Shot(pose.pos(), yaw, pitch, 0, (float) Mth.clamp(pose.fov(), 5, 110));
    }

    public static boolean hidden(Entity entity) {
        Shot shot = lastShot;
        if (shot == null || !active || BATTLER_IDS.contains(entity.getId())) return false;
        if (!(entity instanceof LivingEntity || entity instanceof ItemEntity || entity instanceof VehicleEntity)) return false;
        AABB box = entity.getBoundingBox();
        double grow = Mth.clamp(CULL_FOV / Math.max(1, shot.fov()), 1, CULL_GROW);
        double radius = (hiddenBefore.contains(entity.getId()) ? CULL_KEEP : CULL_RADIUS) * grow;
        boolean hide = box.distanceToSqr(shot.position()) < radius * radius;
        // a Pokemon in the way stays: Hoopa's summons wait in front of it
        if (!hide && !(entity instanceof PokemonEntity)) {
            AABB wide = box.inflate(0.1);
            for (Vec3 subject : SUBJECTS) {
                if (wide.clip(shot.position(), subject).isPresent()) {
                    hide = true;
                    break;
                }
            }
        }
        if (hide) hiddenNow.add(entity.getId());
        return hide;
    }

    public static boolean battler(Entity entity) {
        return active && lastShot != null && BATTLER_IDS.contains(entity.getId());
    }
}
