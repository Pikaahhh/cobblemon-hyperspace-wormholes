package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.entity.WormholeEntity;
import dev.hyperspacewormholes.wormholes.network.RoarPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Your Solgaleo or Lunala roaring a wormhole open in front of it. Doesn't work in Hyperspace.
 */
public final class LegendRoar {
    public static final int ROAR_TICKS = 30;
    private static final int OPEN_AT = 16;
    private static final int COOLDOWN_TICKS = 150;
    private static final double DISTANCE_AHEAD = 14;
    private static final float MAX_PITCH = 45f;
    private static final double LEAD_TICKS = 20;

    private static final Map<String, String> FORM_CRIES = Map.of(
            "800.dusk-mane", "dusk", "800.dawn-wings", "dawn", "800.ultra", "ultra");

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(PokemonEntity mount, int opensAt) {}

    private static int serverTicks;

    private LegendRoar() {}

    public static void request(ServerPlayer player) {
        if (UltraWarpRules.inWarp(player)) return;
        if (!(player.getVehicle() instanceof PokemonEntity mount) || mount.getControllingPassenger() != player) return;
        Pokemon pokemon = Mounts.pokemonOf(mount);
        if (pokemon == null || !Mounts.isAllowed(pokemon)) return;
        boolean airborne = !mount.onGround() && !mount.isInWater()
                && mount.level().noCollision(mount, mount.getBoundingBox().move(0, -1.5, 0));
        if (!airborne || UltraWarpRide.tellRunCooldown(player)) return;

        long now = player.serverLevel().getGameTime();
        Long until = COOLDOWN_UNTIL.get(player.getUUID());
        if (until != null && now < until) return;
        COOLDOWN_UNTIL.put(player.getUUID(), now + COOLDOWN_TICKS);

        RoarPayload roar = new RoarPayload(mount.getId());
        Collection<ServerPlayer> viewers = PlayerLookup.tracking(mount);
        for (ServerPlayer viewer : viewers) ServerPlayNetworking.send(viewer, roar);
        if (!viewers.contains(player)) ServerPlayNetworking.send(player, roar);

        mount.level().playSound(null, mount.getX(), mount.getY(), mount.getZ(),
                SoundEvent.createVariableRangeEvent(cryId(pokemon)), SoundSource.NEUTRAL, 3f, 1f);
        PENDING.add(new Pending(mount, serverTicks + OPEN_AT));
    }

    public static ResourceLocation cryId(Pokemon pokemon) {
        String dex = String.valueOf(pokemon.getSpecies().getNationalPokedexNumber());
        String form = FORM_CRIES.get(dex + "." + pokemon.getForm().getName().toLowerCase(Locale.ROOT));
        return Wormholes.id("cry." + dex + (form != null ? "." + form : ""));
    }

    public static void tick(MinecraftServer server) {
        serverTicks++;
        PENDING.removeIf(p -> {
            if (serverTicks < p.opensAt()) return false;
            if (!p.mount().isRemoved()) openWormhole(p.mount());
            return true;
        });
    }

    private static void openWormhole(PokemonEntity mount) {
        Entity rider = mount.getControllingPassenger();
        if (UltraWarpRules.inHyperspace(mount)) {
            if (rider instanceof ServerPlayer player) UltraWarpRules.tellFailed(player, mount.getName());
            return;
        }
        float yaw = rider != null ? rider.getYRot() : mount.getYRot();
        float pitch = rider != null ? Mth.clamp(rider.getXRot(), -MAX_PITCH, MAX_PITCH) : 0f;
        Vec3 ahead = Vec3.directionFromRotation(pitch, yaw);
        double lead = mount.getDeltaMovement().horizontalDistance() * LEAD_TICKS;
        Vec3 centre = mount.position().add(0, mount.getBbHeight() / 2, 0).add(ahead.scale(DISTANCE_AHEAD + lead));

        ServerLevel level = (ServerLevel) mount.level();
        WormholeEntity wormhole = Wormholes.WORMHOLE.create(level);
        wormhole.setRings(0);
        wormhole.setWormholeType(WormholeType.BLUE);
        wormhole.setLifetime(200);
        wormhole.setSingleUse(true);
        wormhole.moveTo(centre.x, centre.y - WormholeEntity.SIZE / 2, centre.z, yaw + 180f, -pitch);
        level.addFreshEntity(wormhole);
        WormholeSounds.open(level, centre, 0.8f, 1.6f);
    }
}
