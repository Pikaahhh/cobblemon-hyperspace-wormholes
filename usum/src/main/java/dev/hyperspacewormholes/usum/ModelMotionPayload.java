package dev.hyperspacewormholes.usum;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server to nearby clients: after delay seconds, draw entity's model along the curve keys (ModelMotionKeyframe), a
 * flat list of {@value #STRIDE} numbers a key: seconds, forward, up, side, pitch, yaw, roll and the scale along the
 * side, up and facing axes (the keyframe's reach already added to forward on the server).
 */
public record ModelMotionPayload(int entity, float delay, List<Float> keys) implements CustomPacketPayload {
    public static final int STRIDE = 10;
    public static final Type<ModelMotionPayload> TYPE = new Type<>(MoveFx.id("model_motion"));
    public static final StreamCodec<ByteBuf, ModelMotionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ModelMotionPayload::entity,
            ByteBufCodecs.FLOAT, ModelMotionPayload::delay,
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list(STRIDE * 256)), ModelMotionPayload::keys,
            ModelMotionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
