package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server, every tick: how far along the tunnel you are and where you've steered.
 */
public record RideStatePayload(double progress, float offX, float offY) implements CustomPacketPayload {
    public static final Type<RideStatePayload> TYPE = new Type<>(Wormholes.id("ride_state"));
    public static final StreamCodec<ByteBuf, RideStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, RideStatePayload::progress,
            ByteBufCodecs.FLOAT, RideStatePayload::offX,
            ByteBufCodecs.FLOAT, RideStatePayload::offY,
            RideStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
