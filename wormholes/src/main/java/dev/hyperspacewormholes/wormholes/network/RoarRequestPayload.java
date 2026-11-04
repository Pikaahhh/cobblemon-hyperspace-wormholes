package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the rider hit the roar key to open a wormhole. */
public record RoarRequestPayload() implements CustomPacketPayload {
    public static final RoarRequestPayload INSTANCE = new RoarRequestPayload();
    public static final Type<RoarRequestPayload> TYPE = new Type<>(Wormholes.id("roar_request"));
    public static final StreamCodec<ByteBuf, RoarRequestPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
