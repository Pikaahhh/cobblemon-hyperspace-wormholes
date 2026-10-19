package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: fade to white over this many ticks.
 */
public record WarpFlashPayload(int ticks) implements CustomPacketPayload {
    public static final Type<WarpFlashPayload> TYPE = new Type<>(Wormholes.id("warp_flash"));
    public static final StreamCodec<ByteBuf, WarpFlashPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WarpFlashPayload::ticks,
            WarpFlashPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
