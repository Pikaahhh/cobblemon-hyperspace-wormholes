package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.Map;

/**
 * Server to client: what the boss HP bar shows for the boss entity entityId (the real entity, not a battle copy): name
 * (translation key or literal text), level, HP as a fraction of max (the bar shows only the percentage), the shared stat
 * stages (-6..6, zeroes left out), shown (false takes the bar away) and the major status as Showdown's id ("par", "brn",
 * "psn", "tox", "slp", "frz"; empty for none). Sent to each trainer fighting it whenever any of it changes.
 */
public record BossHudPayload(int entityId, String name, int level, float hp, Map<String, Integer> stages, boolean shown,
                             String status)
        implements CustomPacketPayload {
    public static final Type<BossHudPayload> TYPE = new Type<>(Wormholes.id("boss_hud"));
    private static final StreamCodec<ByteBuf, Map<String, Integer>> STAGES =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);
    public static final StreamCodec<ByteBuf, BossHudPayload> CODEC = StreamCodec.of((buf, p) -> {
        ByteBufCodecs.VAR_INT.encode(buf, p.entityId());
        ByteBufCodecs.STRING_UTF8.encode(buf, p.name());
        ByteBufCodecs.VAR_INT.encode(buf, p.level());
        ByteBufCodecs.FLOAT.encode(buf, p.hp());
        STAGES.encode(buf, p.stages());
        ByteBufCodecs.BOOL.encode(buf, p.shown());
        ByteBufCodecs.STRING_UTF8.encode(buf, p.status());
    }, buf -> new BossHudPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.FLOAT.decode(buf), STAGES.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
