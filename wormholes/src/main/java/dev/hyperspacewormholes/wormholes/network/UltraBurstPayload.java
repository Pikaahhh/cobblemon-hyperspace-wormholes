package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: Necrozma Ultra Bursts now and becomes Ultra Necrozma ticks ticks later; the client plays the scene meanwhile. */
public record UltraBurstPayload(int entityId, int ticks) implements CustomPacketPayload {
    public static final Type<UltraBurstPayload> TYPE = new Type<>(Wormholes.id("ultra_burst"));
    public static final StreamCodec<ByteBuf, UltraBurstPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UltraBurstPayload::entityId,
            ByteBufCodecs.VAR_INT, UltraBurstPayload::ticks,
            UltraBurstPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
