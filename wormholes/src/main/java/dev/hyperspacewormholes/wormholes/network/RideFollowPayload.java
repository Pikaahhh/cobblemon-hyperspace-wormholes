package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to a passenger's client, every tick: where the rider in front has flown to.
 */
public record RideFollowPayload(double progress, float offX, float offY) implements CustomPacketPayload {
    public static final Type<RideFollowPayload> TYPE = new Type<>(Wormholes.id("ride_follow"));
    public static final StreamCodec<ByteBuf, RideFollowPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, RideFollowPayload::progress,
            ByteBufCodecs.FLOAT, RideFollowPayload::offX,
            ByteBufCodecs.FLOAT, RideFollowPayload::offY,
            RideFollowPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
