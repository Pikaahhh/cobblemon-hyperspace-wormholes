package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server to client: you're on an Ultra Warp Ride now. Carries the seed, mount, any waystone wormholes, the Ultra Beast
 * override and whether you're a passenger.
 */
public record RideStartPayload(long seed, int mount, List<WaystoneHole> waystones, String override, boolean passenger) implements CustomPacketPayload {
    public static final Type<RideStartPayload> TYPE = new Type<>(Wormholes.id("ride_start"));

    public record WaystoneHole(double distance, float x, float y, int type, String name) {
        public static final StreamCodec<ByteBuf, WaystoneHole> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, WaystoneHole::distance,
                ByteBufCodecs.FLOAT, WaystoneHole::x,
                ByteBufCodecs.FLOAT, WaystoneHole::y,
                ByteBufCodecs.VAR_INT, WaystoneHole::type,
                ByteBufCodecs.STRING_UTF8, WaystoneHole::name,
                WaystoneHole::new);
    }

    public static final StreamCodec<ByteBuf, RideStartPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, RideStartPayload::seed,
            ByteBufCodecs.VAR_INT, RideStartPayload::mount,
            WaystoneHole.CODEC.apply(ByteBufCodecs.list()), RideStartPayload::waystones,
            ByteBufCodecs.STRING_UTF8, RideStartPayload::override,
            ByteBufCodecs.BOOL, RideStartPayload::passenger,
            RideStartPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
