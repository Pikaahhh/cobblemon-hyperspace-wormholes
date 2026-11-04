package dev.hyperspacewormholes.usum;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server to nearby clients: shake the camera (CameraShakeKeyframe), fading with distance from the battle's centre. */
public record CameraShakePayload(Vec3 centre, float delay, float duration, float from, float to, float rate)
        implements CustomPacketPayload {
    public static final Type<CameraShakePayload> TYPE = new Type<>(MoveFx.id("camera_shake"));
    private static final StreamCodec<ByteBuf, Vec3> VEC3 = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, vec -> vec.x,
            ByteBufCodecs.DOUBLE, vec -> vec.y,
            ByteBufCodecs.DOUBLE, vec -> vec.z,
            Vec3::new);
    public static final StreamCodec<ByteBuf, CameraShakePayload> CODEC = StreamCodec.composite(
            VEC3, CameraShakePayload::centre,
            ByteBufCodecs.FLOAT, CameraShakePayload::delay,
            ByteBufCodecs.FLOAT, CameraShakePayload::duration,
            ByteBufCodecs.FLOAT, CameraShakePayload::from,
            ByteBufCodecs.FLOAT, CameraShakePayload::to,
            ByteBufCodecs.FLOAT, CameraShakePayload::rate,
            CameraShakePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
