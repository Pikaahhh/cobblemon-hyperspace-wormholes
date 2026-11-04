package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: done watching yourself come out of the wormhole. */
public record RiderEmergeDonePayload() implements CustomPacketPayload {
    public static final RiderEmergeDonePayload INSTANCE = new RiderEmergeDonePayload();
    public static final Type<RiderEmergeDonePayload> TYPE = new Type<>(Wormholes.id("rider_emerge_done"));
    public static final StreamCodec<ByteBuf, RiderEmergeDonePayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
