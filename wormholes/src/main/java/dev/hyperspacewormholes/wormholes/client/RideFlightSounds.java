package dev.hyperspacewormholes.wormholes.client;

import dev.hyperspacewormholes.wormholes.ride.RideLegend;
import dev.hyperspacewormholes.wormholes.ride.WarpTunnel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * Plays the legend's own flying sounds during the ride, since Cobblemon's go quiet while the mount's held still in
 * Ultra Space.
 */
public class RideFlightSounds extends AbstractTickableSoundInstance {
    private static final ResourceLocation WIND = ResourceLocation.fromNamespaceAndPath("cobblemon", "ride.loop.wind.stereo");
    private static final ResourceLocation LEATHER = ResourceLocation.fromNamespaceAndPath("cobblemon", "ride.loop.leather.stereo");
    private static final float FLIGHT_PITCH = 1.2f;

    public static List<RideFlightSounds> forMount(RideLegend mount) {
        return mount == RideLegend.LUNALA
                ? List.of(new RideFlightSounds(WIND), new RideFlightSounds(LEATHER))
                : List.of(new RideFlightSounds(WIND));
    }

    private RideFlightSounds(ResourceLocation sound) {
        super(SoundEvent.createVariableRangeEvent(sound), SoundSource.NEUTRAL, RandomSource.create());
        this.looping = true;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.01f;
        this.pitch = FLIGHT_PITCH;
    }

    @Override
    public void tick() {
        if (!RideClient.isActive()) {
            stop();
            return;
        }
        float rush = Mth.clamp((RideClient.speed() - WarpTunnel.CRUISE_SPEED)
                / (WarpTunnel.BOOST_SPEED - WarpTunnel.CRUISE_SPEED), 0f, 1f);
        volume = Math.min(1f, RideClient.time(0) / 30f);
        pitch = FLIGHT_PITCH + 0.2f * rush;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
