package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the rider hit the roar key to open a wormhole, or the key for one that leads to waystones.
 */
public record RoarRequestPayload(boolean waystones) implements CustomPacketPayload {
    public static final Type<RoarRequestPayload> TYPE = new Type<>(Wormholes.id("roar_request"));
    public static final StreamCodec<ByteBuf, RoarRequestPayload> CODEC =
            ByteBufCodecs.BOOL.map(RoarRequestPayload::new, RoarRequestPayload::waystones);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
