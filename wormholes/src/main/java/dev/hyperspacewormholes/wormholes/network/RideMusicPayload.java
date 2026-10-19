package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: start the Ultra Wormhole music now, right before the teleport.
 */
public record RideMusicPayload() implements CustomPacketPayload {
    public static final RideMusicPayload INSTANCE = new RideMusicPayload();
    public static final Type<RideMusicPayload> TYPE = new Type<>(Wormholes.id("ride_music"));
    public static final StreamCodec<ByteBuf, RideMusicPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
