package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the ride's over.
 */
public record RideEndPayload() implements CustomPacketPayload {
    public static final RideEndPayload INSTANCE = new RideEndPayload();
    public static final Type<RideEndPayload> TYPE = new Type<>(Wormholes.id("ride_end"));
    public static final StreamCodec<ByteBuf, RideEndPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
