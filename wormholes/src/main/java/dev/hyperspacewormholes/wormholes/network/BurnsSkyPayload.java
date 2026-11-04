package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to nearby clients: a Pokemon starts Light That Burns the Sky, with one of its targets (targetId), its mouth
 * and facing, the middle of its targets (targetCentre, and targetFeet on the floor, where the ball lands) and how long
 * the ball takes to hit.
 */
public record BurnsSkyPayload(int userId, int targetId, Vec3 mouth, Vec3 dir, Vec3 targetCentre, Vec3 targetFeet,
                              int travelTicks)
        implements CustomPacketPayload {
    public static final Type<BurnsSkyPayload> TYPE = new Type<>(Wormholes.id("burns_sky"));
    /** Vec3 has no stream codec of its own. */
    static final StreamCodec<ByteBuf, Vec3> VEC3 = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x,
            ByteBufCodecs.DOUBLE, Vec3::y,
            ByteBufCodecs.DOUBLE, Vec3::z,
            Vec3::new);
    public static final StreamCodec<ByteBuf, BurnsSkyPayload> CODEC = StreamCodec.of((buf, p) -> {
        ByteBufCodecs.VAR_INT.encode(buf, p.userId());
        ByteBufCodecs.VAR_INT.encode(buf, p.targetId());
        VEC3.encode(buf, p.mouth());
        VEC3.encode(buf, p.dir());
        VEC3.encode(buf, p.targetCentre());
        VEC3.encode(buf, p.targetFeet());
        ByteBufCodecs.VAR_INT.encode(buf, p.travelTicks());
    }, buf -> new BurnsSkyPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), VEC3.decode(buf), VEC3.decode(buf),
            VEC3.decode(buf), VEC3.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
