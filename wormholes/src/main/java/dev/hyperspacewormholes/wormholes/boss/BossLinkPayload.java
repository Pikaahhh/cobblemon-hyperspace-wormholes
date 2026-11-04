package dev.hyperspacewormholes.wormholes.boss;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Server to clients: the battle Pokemon with this uuid (one trainer's copy of a boss) is drawn by the entity with this
 * id, the real boss; an entity id of -1 drops the link. Client code that finds a battler's entity by its uuid asks
 * client.BossClient.entity, since Cobblemon's own lookup only knows the boss's own Pokemon.
 */
public record BossLinkPayload(UUID pokemon, int entityId) implements CustomPacketPayload {
    public static final Type<BossLinkPayload> TYPE = new Type<>(Wormholes.id("boss_link"));
    public static final StreamCodec<ByteBuf, BossLinkPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, BossLinkPayload::pokemon,
            ByteBufCodecs.VAR_INT, BossLinkPayload::entityId,
            BossLinkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
