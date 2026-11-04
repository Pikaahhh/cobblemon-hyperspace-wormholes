package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server to client: you're on an Ultra Warp Ride now. Carries the seed, mount, any waystone wormholes, whether you're a
 * passenger, whether the tunnel's wormholes are in the game's colours and, on a ride to one destination, the one colour
 * (WormholeType id, -1 for none) and rings every wormhole in the tunnel has.
 */
public record RideStartPayload(long seed, int mount, List<WaystoneHole> waystones, boolean passenger,
                               boolean gameColours, int only, int onlyRings) implements CustomPacketPayload {
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

    private static final StreamCodec<ByteBuf, List<WaystoneHole>> HOLES = WaystoneHole.CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, RideStartPayload> CODEC = StreamCodec.of((buf, p) -> {
        ByteBufCodecs.VAR_LONG.encode(buf, p.seed());
        ByteBufCodecs.VAR_INT.encode(buf, p.mount());
        HOLES.encode(buf, p.waystones());
        ByteBufCodecs.BOOL.encode(buf, p.passenger());
        ByteBufCodecs.BOOL.encode(buf, p.gameColours());
        ByteBufCodecs.VAR_INT.encode(buf, p.only());
        ByteBufCodecs.VAR_INT.encode(buf, p.onlyRings());
    }, buf -> new RideStartPayload(ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), HOLES.decode(buf),
            ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
