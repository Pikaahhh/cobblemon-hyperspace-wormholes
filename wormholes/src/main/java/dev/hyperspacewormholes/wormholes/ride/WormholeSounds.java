package dev.hyperspacewormholes.wormholes.ride;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Plays a wormhole opening so anyone within 128 blocks hears it, quieter the further away they are.
 */
public final class WormholeSounds {
    private static final double HEARD_WITHIN = 128;
    private static final double TOWARD = 4;
    private static final float QUIETEST = 0.3f;

    private WormholeSounds() {}

    public static void open(ServerLevel level, Vec3 at, float volume, float pitch) {
        Holder<SoundEvent> sound = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.END_PORTAL_SPAWN);
        long seed = level.getRandom().nextLong();
        double earshot = 16 * Math.max(1f, volume) * 0.8;
        for (ServerPlayer player : level.players()) {
            double across = Math.hypot(player.getX() - at.x, player.getZ() - at.z);
            if (across > HEARD_WITHIN) continue;
            Vec3 ear = player.getEyePosition();
            Vec3 from = at;
            float loudness = volume;
            if (ear.distanceTo(at) > earshot) {
                from = ear.add(at.subtract(ear).normalize().scale(TOWARD));
                loudness = Mth.lerp((float) (across / HEARD_WITHIN), 1f, QUIETEST);
            }
            player.connection.send(new ClientboundSoundPacket(sound, SoundSource.NEUTRAL, from.x, from.y, from.z, loudness, pitch, seed));
        }
    }
}
