package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.client.cinematic.Cinematics;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While one of Necrozma's cutscenes plays (Cinematics), the camera goes where the game's cutscene camera is: position,
 * yaw, pitch and a roll vanilla's camera never has, with its rotation and direction vectors rebuilt to match. The field
 * of view is set in CinematicProjectionMixin. The high priority makes this win over other camera mixins.
 */
@Mixin(value = Camera.class, priority = 1500)
public abstract class CinematicCameraMixin {
    @Shadow
    @Final
    private static Vector3f FORWARDS;

    @Shadow
    @Final
    private static Vector3f UP;

    @Shadow
    @Final
    private static Vector3f LEFT;

    @Shadow
    @Final
    private Quaternionf rotation;

    @Shadow
    @Final
    private Vector3f forwards;

    @Shadow
    @Final
    private Vector3f up;

    @Shadow
    @Final
    private Vector3f left;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void hyperspace_wormholes$cinematicCamera(BlockGetter level, Entity entity, boolean thirdPerson, boolean mirrored,
                                                      float partialTick, CallbackInfo ci) {
        double[] view = Cinematics.camera(partialTick);
        if (view == null) return;
        float yaw = (float) view[3], pitch = (float) view[4], roll = (float) view[5];
        setRotation(yaw, pitch);
        if (roll != 0) {
            rotation.rotationYXZ(Mth.PI - yaw * Mth.DEG_TO_RAD, -pitch * Mth.DEG_TO_RAD, -roll * Mth.DEG_TO_RAD);
            FORWARDS.rotate(rotation, forwards);
            UP.rotate(rotation, up);
            LEFT.rotate(rotation, left);
        }
        setPosition(new Vec3(view[0], view[1], view[2]));
    }
}
