package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server to client: play one of Necrozma's boss-fight scenes on the entity entityId over ticks ticks: POWER_UP or DEFEAT. at
 * and yaw are where the scene is played and the way Necrozma faces there; without them (at null) the client takes the
 * entity's own position and body yaw. The Ultra Burst itself is UltraBurstPayload.
 */
public record NecrozmaScenePayload(int kind, int entityId, int ticks, @Nullable Vec3 at, float yaw) implements CustomPacketPayload {
    public static final int POWER_UP = 0, DEFEAT = 1;
    public static final Type<NecrozmaScenePayload> TYPE = new Type<>(Wormholes.id("necrozma_scene"));
    public static final StreamCodec<ByteBuf, NecrozmaScenePayload> CODEC = StreamCodec.of((buf, p) -> {
        ByteBufCodecs.VAR_INT.encode(buf, p.kind);
        ByteBufCodecs.VAR_INT.encode(buf, p.entityId);
        ByteBufCodecs.VAR_INT.encode(buf, p.ticks);
        ByteBufCodecs.BOOL.encode(buf, p.at != null);
        if (p.at != null) {
            BurnsSkyPayload.VEC3.encode(buf, p.at);
            ByteBufCodecs.FLOAT.encode(buf, p.yaw);
        }
    }, buf -> {
        int kind = ByteBufCodecs.VAR_INT.decode(buf), entityId = ByteBufCodecs.VAR_INT.decode(buf), ticks = ByteBufCodecs.VAR_INT.decode(buf);
        if (!ByteBufCodecs.BOOL.decode(buf)) return new NecrozmaScenePayload(kind, entityId, ticks);
        Vec3 at = BurnsSkyPayload.VEC3.decode(buf);
        return new NecrozmaScenePayload(kind, entityId, ticks, at, ByteBufCodecs.FLOAT.decode(buf));
    });

    public NecrozmaScenePayload(int kind, int entityId, int ticks) {
        this(kind, entityId, ticks, null, 0f);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
