package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to nearby clients: a Light That Burns the Sky was cut short (its user went before the ball hit). */
public record BurnsSkyStopPayload(int userId) implements CustomPacketPayload {
    public static final Type<BurnsSkyStopPayload> TYPE = new Type<>(Wormholes.id("burns_sky_stop"));
    public static final StreamCodec<ByteBuf, BurnsSkyStopPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BurnsSkyStopPayload::userId,
            BurnsSkyStopPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
