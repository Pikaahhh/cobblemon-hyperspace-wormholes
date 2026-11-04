package dev.hyperspacewormholes.wormholes.boss;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to a trainer in a boss battle: how many active slots the wild side has now (LegendWave). Cobblemon fixes these
 * when a battle starts; BossClient grows or shrinks them before the BattleSwitchPokemonPackets that fill them arrive.
 */
public record BossSlotsPayload(int count) implements CustomPacketPayload {
    public static final Type<BossSlotsPayload> TYPE = new Type<>(Wormholes.id("boss_slots"));
    public static final StreamCodec<ByteBuf, BossSlotsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BossSlotsPayload::count,
            BossSlotsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
