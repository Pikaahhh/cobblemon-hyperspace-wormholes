package dev.hyperspacewormholes.wormholes.client;

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PrimaryAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockActiveAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimation;
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.client.cinematic.Cinematics;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Keeps a beaten Necrozma lying on the floor in its weakened pose until it is caught. Every wild Necrozma in the Ultra
 * Megalopolis in its own form (read from the synced aspects, since this client's copy of the Pokemon keeps the aspects it
 * was sent out with) within RANGE plays the "weakened" animation as a Cobblemon primary animation, sunk into its floor
 * (LYING) by NecrozmaSceneCinematic.sink. A beaten Ultra Necrozma stays down in "defeat_down" (keepDown) until the server
 * makes it base Necrozma, and once weakened it never turns (FACING).
 */
public final class NecrozmaWeakened {
    private static final double RANGE = 96;
    private static final String MOTIONS = "necrozma_ultraburst", WEAKENED = "weakened", DOWN_POSE = "defeat_down";
    private static final String ULTRA = "ultra-fusion", DIM_ASPECT = "ultra-dim-3";
    private static final Set<String> FUSED = Set.of("dusk-fusion", "dawn-fusion", ULTRA);
    private static final Map<PosableState, PrimaryAnimation> PLAYING = new WeakHashMap<>();
    private static final Map<Integer, Down> DOWN = new HashMap<>();
    private static final Map<Integer, Float> FACING = new HashMap<>();
    private static final Map<Integer, Double> LYING = new HashMap<>();
    private static final int FLOOR_SEARCH = 6;

    private record Down(Vec3 at, float yaw, double floor) {}

    private NecrozmaWeakened() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(NecrozmaWeakened::tick);
    }

    public static void keepDown(PokemonEntity necrozma, Vec3 at, float yaw, double floor) {
        DOWN.put(necrozma.getId(), new Down(at, yaw, floor));
    }

    public static @Nullable Double downFloor(int entityId) {
        Down down = DOWN.get(entityId);
        return down == null ? null : down.floor;
    }

    public static @Nullable Double weakFloor(int entityId) {
        return LYING.get(entityId);
    }

    public static boolean holdsDown() {
        return !DOWN.isEmpty();
    }

    public static void release(int entityId) {
        DOWN.remove(entityId);
        LYING.remove(entityId);
    }

    public static void start(PokemonEntity necrozma, float yaw, double floor) {
        LYING.put(necrozma.getId(), floor);
        FACING.put(necrozma.getId(), yaw);
        face(necrozma, yaw);
        if (necrozma.getDelegate() instanceof PosableState state) play(state, WEAKENED, false);
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            DOWN.clear();
            FACING.clear();
            LYING.clear();
            return;
        }
        FACING.keySet().removeIf(id -> !(mc.level.getEntity(id) instanceof PokemonEntity p) || p.isRemoved() || p.getOwner() != null);
        LYING.keySet().removeIf(id -> !(mc.level.getEntity(id) instanceof PokemonEntity p) || p.isRemoved() || p.getOwner() != null
                || fused(p));
        DOWN.entrySet().removeIf(entry -> {
            if (mc.level.getEntity(entry.getKey()) instanceof PokemonEntity p && !p.isRemoved()
                    && p.getEntityData().get(PokemonEntity.getASPECTS()).contains(ULTRA)) return false;
            LYING.put(entry.getKey(), entry.getValue().floor);
            return true;
        });
        for (Map.Entry<Integer, Down> entry : DOWN.entrySet()) {
            PokemonEntity pokemon = (PokemonEntity) mc.level.getEntity(entry.getKey());
            if (Cinematics.isBoss(pokemon) || !(pokemon.getDelegate() instanceof PosableState state)) continue;
            hold(pokemon, entry.getValue());
            play(state, DOWN_POSE, true);
        }
        if (mc.level.dimension() != Wormholes.ULTRA_MEGALOPOLIS) {
            LYING.clear();
            return;
        }
        for (Entity entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(RANGE))) {
            if (!(entity instanceof PokemonEntity pokemon) || pokemon.getOwner() != null
                    || !pokemon.getPokemon().getSpecies().getName().equalsIgnoreCase("necrozma")
                    || fused(pokemon) || !(pokemon.getDelegate() instanceof PosableState state)) continue;
            LYING.computeIfAbsent(pokemon.getId(), id -> floorUnder(mc, pokemon));
            float yaw = FACING.computeIfAbsent(pokemon.getId(), id -> {
                Wormholes.LOGGER.info("[necrozma] weakened facing for {}: yRot {} body {} head {}", id, pokemon.getYRot(),
                        pokemon.yBodyRot, pokemon.getYHeadRot());
                return pokemon.getYRot();
            });
            if (!Cinematics.isBoss(pokemon)) face(pokemon, yaw);
            play(state, WEAKENED, true);
        }
    }

    private static boolean fused(PokemonEntity pokemon) {
        return pokemon.getEntityData().get(PokemonEntity.getASPECTS()).stream().anyMatch(FUSED::contains);
    }

    private static double floorUnder(Minecraft mc, PokemonEntity pokemon) {
        BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
        double y = pokemon.getY();
        for (int dy = 0; dy <= FLOOR_SEARCH; dy++) {
            block.set(Mth.floor(pokemon.getX()), Mth.floor(y + 0.5) - dy, Mth.floor(pokemon.getZ()));
            var shape = mc.level.getBlockState(block).getCollisionShape(mc.level, block);
            if (shape.isEmpty()) continue;
            double top = block.getY() + shape.max(Direction.Axis.Y);
            return top <= y + 0.5 ? top : y;
        }
        return y;
    }

    private static void face(PokemonEntity pokemon, float yaw) {
        turn(pokemon, yaw);
        pokemon.xRotO = 0;
        pokemon.setXRot(0);
    }

    private static void turn(PokemonEntity pokemon, float yaw) {
        pokemon.yBodyRotO = yaw;
        pokemon.setYBodyRot(yaw);
        pokemon.yHeadRotO = yaw;
        pokemon.setYHeadRot(yaw);
        pokemon.yRotO = yaw;
        pokemon.setYRot(yaw);
    }

    private static void hold(PokemonEntity pokemon, Down down) {
        pokemon.setPos(down.at);
        pokemon.xo = down.at.x;
        pokemon.yo = down.at.y;
        pokemon.zo = down.at.z;
        pokemon.setDeltaMovement(Vec3.ZERO);
        turn(pokemon, down.yaw);
        Set<String> aspects = pokemon.getEntityData().get(PokemonEntity.getASPECTS());
        if (!aspects.contains(DIM_ASPECT)) {
            Set<String> next = new HashSet<>();
            for (String a : aspects) if (!a.startsWith("ultra-dim-")) next.add(a);
            next.add(DIM_ASPECT);
            pokemon.getEntityData().set(PokemonEntity.getASPECTS(), next);
        }
    }

    private static void play(PosableState state, String name, boolean blend) {
        PrimaryAnimation mine = PLAYING.get(state);
        if (mine != null && state.getPrimaryAnimation() == mine && mine.getAnimation() instanceof BedrockActiveAnimation a
                && a.getAnimation().getName().endsWith("." + name)) return;
        BedrockAnimation animation = BedrockAnimationRepository.INSTANCE.getAnimationOrNull(MOTIONS, "animation." + MOTIONS + "." + name);
        if (animation == null) return;
        PrimaryAnimation primary = new PrimaryAnimation(new BedrockActiveAnimation(animation), t -> 1f, Set.of(), blend && mine == null);
        state.addPrimaryAnimation(primary);
        PLAYING.put(state, primary);
    }
}
