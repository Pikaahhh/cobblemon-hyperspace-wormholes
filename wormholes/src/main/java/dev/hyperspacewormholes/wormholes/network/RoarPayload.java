package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to nearby clients: this legend is roaring.
 */
public record RoarPayload(int entityId) implements CustomPacketPayload {
    public static final Type<RoarPayload> TYPE = new Type<>(Wormholes.id("roar"));
    public static final StreamCodec<ByteBuf, RoarPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RoarPayload::entityId,
            RoarPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
