package dev.hyperspacewormholes.wormholes.boss;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Server to a trainer fighting a boss: the Showdown ids of the moves their Pokemon can't use in this fight (BossDef.bannedMoves; their move requests show them disabled, greyed out); an empty list when they stop fighting it. */
public record BossBanPayload(List<String> moves) implements CustomPacketPayload {
    public static final Type<BossBanPayload> TYPE = new Type<>(Wormholes.id("boss_ban"));
    public static final StreamCodec<ByteBuf, BossBanPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), BossBanPayload::moves,
            BossBanPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
