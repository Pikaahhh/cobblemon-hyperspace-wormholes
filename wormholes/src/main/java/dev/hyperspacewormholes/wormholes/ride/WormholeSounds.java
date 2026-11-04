package dev.hyperspacewormholes.wormholes.ride;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Plays a wormhole opening so anyone within 128 blocks hears it, quieter the further away they are, and a wormhole
 * Pokemon's cry. Cobblemon's cry only plays its model's cry animation (silent for models without one), so cry also sends
 * the species' own cry sound to anyone within CRY_HEARD_WITHIN. rumble is the ground rumbling before an Ultra Beast leaps
 * up (ZoneLeaps), heard within RUMBLE_HEARD_WITHIN.
 */
public final class WormholeSounds {
    private static final double HEARD_WITHIN = 128;
    private static final double TOWARD = 4;
    private static final float QUIETEST = 0.3f;
    private static final double CRY_HEARD_WITHIN = 96;
    private static final double RUMBLE_HEARD_WITHIN = 160;

    private WormholeSounds() {}

    public static void open(ServerLevel level, Vec3 at, float volume, float pitch) {
        send(level, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.END_PORTAL_SPAWN), at, volume, pitch, HEARD_WITHIN);
    }

    public static void rumble(ServerLevel level, Vec3 at) {
        send(level, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.WARDEN_EMERGE), at, 2f, 0.7f, RUMBLE_HEARD_WITHIN);
    }

    public static void cry(PokemonEntity entity) {
        entity.cry();
        ResourceLocation species = entity.getPokemon().getSpecies().getResourceIdentifier();
        SoundEvent cry = SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(species.getNamespace(), "pokemon." + species.getPath() + ".cry"));
        if (entity.level() instanceof ServerLevel level) {
            send(level, Holder.direct(cry), entity.getEyePosition(), 1.5f, 1f, CRY_HEARD_WITHIN);
        }
    }

    private static void send(ServerLevel level, Holder<SoundEvent> sound, Vec3 at, float volume, float pitch, double heardWithin) {
        long seed = level.getRandom().nextLong();
        double earshot = 16 * Math.max(1f, volume) * 0.8;
        for (ServerPlayer player : level.players()) {
            double across = Math.hypot(player.getX() - at.x, player.getZ() - at.z);
            if (across > heardWithin) continue;
            Vec3 ear = player.getEyePosition();
            Vec3 from = at;
            float loudness = volume;
            if (ear.distanceTo(at) > earshot) {
                from = ear.add(at.subtract(ear).normalize().scale(TOWARD));
                loudness = Mth.lerp((float) (across / heardWithin), 1f, QUIETEST);
            }
            player.connection.send(new ClientboundSoundPacket(sound, SoundSource.NEUTRAL, from.x, from.y, from.z, loudness, pitch, seed));
        }
    }
}
