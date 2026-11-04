package dev.hyperspacewormholes.wormholes.boss;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to a trainer fighting a boss: whether their locked battle menu is waiting for the others' picks (Boss.round),
 * shown under Cobblemon's "Press ` to move around." hint (BossWaitHintMixin). PICKS only while they have picked and
 * another trainer hasn't, else NONE.
 */
public record BossWaitPayload(int state) implements CustomPacketPayload {
    public static final int NONE = 0, PICKS = 1;
    public static final Type<BossWaitPayload> TYPE = new Type<>(Wormholes.id("boss_wait"));
    public static final StreamCodec<ByteBuf, BossWaitPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BossWaitPayload::state,
            BossWaitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
