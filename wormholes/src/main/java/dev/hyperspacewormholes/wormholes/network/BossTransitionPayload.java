package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: play a boss screen transition now (client.BossTransition): flash ticks of the two white flashes the
 * Pokemon Emerald battle transitions open with (none when 0), close ticks of black bars sliding in (rows when vertical
 * is false, columns when true), hold ticks of black, then open ticks of the bars sliding back out. The server moves
 * things the players mustn't see while the screen is black, flash + close ticks in. The HUD and battle screen stay
 * hidden for ui more ticks after it ends.
 */
public record BossTransitionPayload(boolean vertical, int flash, int close, int hold, int open, int ui) implements CustomPacketPayload {
    public static final Type<BossTransitionPayload> TYPE = new Type<>(Wormholes.id("boss_transition"));
    public static final StreamCodec<ByteBuf, BossTransitionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BossTransitionPayload::vertical,
            ByteBufCodecs.VAR_INT, BossTransitionPayload::flash,
            ByteBufCodecs.VAR_INT, BossTransitionPayload::close,
            ByteBufCodecs.VAR_INT, BossTransitionPayload::hold,
            ByteBufCodecs.VAR_INT, BossTransitionPayload::open,
            ByteBufCodecs.VAR_INT, BossTransitionPayload::ui,
            BossTransitionPayload::new);

    public int black() {
        return flash + close;
    }

    public int length() {
        return flash + close + hold + open;
    }

    public BossTransitionPayload hidingUi(int ticks) {
        return new BossTransitionPayload(vertical, flash, close, hold, open, ticks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
