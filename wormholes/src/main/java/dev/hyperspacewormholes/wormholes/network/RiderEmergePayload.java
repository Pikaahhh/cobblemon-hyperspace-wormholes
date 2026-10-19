package dev.hyperspacewormholes.wormholes.network;

import dev.hyperspacewormholes.wormholes.Wormholes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: a rider (and their passengers) is coming out of a wormhole onto a landing spot, plus the timings.
 */
public record RiderEmergePayload(int riderId, List<Integer> passengerIds, Vec3 mouth, Vec3 landing, int emergeTicks, int settleTicks, boolean airborne)
        implements CustomPacketPayload {
    public static final Type<RiderEmergePayload> TYPE = new Type<>(Wormholes.id("rider_emerge"));
    public static final StreamCodec<ByteBuf, RiderEmergePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.riderId);
                buf.writeInt(p.passengerIds.size());
                for (int id : p.passengerIds) buf.writeInt(id);
                writeVec(buf, p.mouth);
                writeVec(buf, p.landing);
                buf.writeInt(p.emergeTicks);
                buf.writeInt(p.settleTicks);
                buf.writeBoolean(p.airborne);
            },
            buf -> new RiderEmergePayload(buf.readInt(), readIds(buf), readVec(buf), readVec(buf), buf.readInt(), buf.readInt(), buf.readBoolean()));

    private static void writeVec(ByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static List<Integer> readIds(ByteBuf buf) {
        int n = buf.readInt();
        List<Integer> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ids.add(buf.readInt());
        return ids;
    }

    private static Vec3 readVec(ByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
