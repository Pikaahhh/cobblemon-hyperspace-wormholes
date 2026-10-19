package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Random event: every now and then a wormhole opens high in the sky near a player and flings a Pokemon (sometimes an
 * Ultra Beast) down next to them and anyone nearby.
 */
public final class SkyWormholes {
    private static final int EVERY_TICKS = 10 * 60 * 20;
    private static final double CHANCE = 0.25;
    private static final double ULTRA_BEAST_CHANCE = 0.05;
    private static final int LANDING_REACH = 16;
    private static final int LANDING_MIN = 6;
    private static final int LOOK_UP = 8;
    private static final int LOOK_DOWN = 12;
    private static final int SKY_HIGH = 75;
    private static final int SKY_LOW = 50;
    private static final int SIDE_NEAR = 12;
    private static final int SIDE_FAR = 26;
    private static final int NEARBY = 48;
    private static final int TRIES = 16;

    private SkyWormholes() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % EVERY_TICKS != 0 || server.getTickCount() == 0) return;

        RandomSource random = server.overworld().getRandom();
        if (random.nextDouble() >= CHANCE) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers().stream().filter(SkyWormholes::eligible).toList();
        if (players.isEmpty()) return;
        open(players.get(random.nextInt(players.size())));
    }

    private static boolean eligible(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        return player.isAlive() && !player.isSpectator() && !UltraDimensions.isUltra(level)
                && !UltraWarpRules.inHyperspace(player) && level.dimension() != Level.NETHER;
    }

    public static boolean open(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();
        List<ServerPlayer> near = level.players().stream()
                .filter(p -> p == player || eligible(p) && p.distanceToSqr(player) <= NEARBY * NEARBY).toList();
        for (int attempt = 0; attempt < TRIES; attempt++) {
            PokemonEntity first = pokemon(level, player, random);
            if (first == null) return false;
            Vec3 landing = landing(level, player, first, random);
            if (landing == null) continue;
            double facing = random.nextDouble() * Math.PI * 2;
            double side = SIDE_NEAR + random.nextDouble() * (SIDE_FAR - SIDE_NEAR);
            Vec3 mouth = mouth(level, player, landing, facing, side);
            if (mouth == null) continue;
            float pitch = (float) (Mth.atan2(mouth.y - landing.y, side) * Mth.RAD_TO_DEG);
            List<PokemonEntity> entities = new ArrayList<>(List.of(first));
            List<Vec3> landings = new ArrayList<>(List.of(landing));
            List<ServerPlayer> targets = new ArrayList<>(List.of(player));
            for (ServerPlayer other : near) {
                if (other == player) continue;
                PokemonEntity entity = pokemon(level, other, random);
                Vec3 spot = entity == null ? null : landing(level, other, entity, random);
                if (spot == null) continue;
                entities.add(entity);
                landings.add(spot);
                targets.add(other);
            }
            List<Float> yaws = new ArrayList<>();
            boolean ultraBeast = false;
            for (int i = 0; i < entities.size(); i++) {
                Vec3 toPlayer = targets.get(i).position().subtract(landings.get(i));
                yaws.add((float) (Mth.atan2(-toPlayer.x, toPlayer.z) * Mth.RAD_TO_DEG));
                ultraBeast |= entities.get(i).getPokemon().getSpecies().getLabels().contains("ultra_beast");
            }
            float yaw = (float) (Mth.atan2(-Math.cos(facing + Math.PI), Math.sin(facing + Math.PI)) * Mth.RAD_TO_DEG);
            PokemonArrivals.launch(level, entities, landings, yaws, mouth, yaw, pitch, ultraBeast ? WormholeType.WHITE : WormholeType.GREEN);
            return true;
        }
        return false;
    }

    private static @Nullable PokemonEntity pokemon(ServerLevel level, ServerPlayer player, RandomSource random) {
        List<ElementalType> types = ElementalTypes.all();
        String type = types.get(random.nextInt(types.size())).getName().toLowerCase(Locale.ROOT);
        String properties = random.nextDouble() < ULTRA_BEAST_CHANCE ? WormholePokemon.ultraBeastOfType(type, random) : null;
        if (properties == null) properties = WormholePokemon.regularOfType(type, random);
        if (properties == null) return null;
        PokemonEntity entity;
        try {
            entity = PokemonProperties.Companion.parse(properties).createEntity(level);
        } catch (RuntimeException e) {
            Wormholes.LOGGER.error("Couldn't make the sky wormhole's Pokémon '{}'", properties, e);
            return null;
        }
        WormholePokemon.atLeastAverageSize(entity.getPokemon());
        entity.getPokemon().setShiny(WormholePokemon.skyShiny(entity.getPokemon(), player, random));
        entity.refreshDimensions();
        return entity;
    }

    private static @Nullable Vec3 landing(ServerLevel level, ServerPlayer player, PokemonEntity entity, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = LANDING_MIN + random.nextDouble() * (LANDING_REACH - LANDING_MIN);
        int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
        if (!level.isLoaded(new BlockPos(x, player.getBlockY(), z))) return null;
        for (int y = player.getBlockY() + LOOK_UP; y >= player.getBlockY() - LOOK_DOWN; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos below = pos.below();
            if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) continue;
            if (level.getFluidState(pos).is(FluidTags.LAVA) || level.getFluidState(below).is(FluidTags.LAVA)) return null;
            Vec3 spot = Vec3.atBottomCenterOf(pos);
            entity.setPos(spot);
            if (level.noCollision(entity)) return spot;
        }
        return null;
    }

    private static @Nullable Vec3 mouth(ServerLevel level, ServerPlayer player, Vec3 landing, double facing, double side) {
        int top = (int) Math.min(player.getY() + SKY_HIGH, level.getMaxBuildHeight() - 8);
        for (int y = top; y >= player.getY() + SKY_LOW; y--) {
            Vec3 mouth = new Vec3(landing.x + Math.cos(facing) * side, y, landing.z + Math.sin(facing) * side);
            if (level.isLoaded(BlockPos.containing(mouth)) && PokemonArrivals.portalClear(level, mouth)) return mouth;
        }
        return null;
    }
}
