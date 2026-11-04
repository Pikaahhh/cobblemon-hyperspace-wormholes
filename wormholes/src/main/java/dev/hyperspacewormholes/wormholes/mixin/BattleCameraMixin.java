package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.client.battlecamera.BattleCamera;
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
 * Puts the camera where the battle camera wants it once vanilla has lined it up with the player: position, yaw, pitch and the game's slight roll (the rotation and direction vectors rebuilt with it, as cutscene camera does), marked detached so the player's own body can be drawn (BattleEntityMixin leaves it out near the shot); the shot's field of view goes on in BattleViewMixin.
 * Priority 900 so it runs before the move animations' camera shake (CameraShakeMixin, which then turns this shot) and before cutscene camera (priority 1500), which wins while it plays. Optional (require 0).
 */
@Mixin(value = Camera.class, priority = 900)
public abstract class BattleCameraMixin {
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
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void hyperspace_wormholes$battleCamera(BlockGetter level, Entity entity, boolean thirdPerson, boolean mirrored,
                                                   float partialTick, CallbackInfo ci) {
        BattleCamera.Shot shot = BattleCamera.frame(partialTick);
        if (shot == null) return;
        detached = true;
        setRotation(shot.yaw(), shot.pitch());
        if (Math.abs(shot.roll()) > 0.01f) {
            rotation.rotationYXZ(Mth.PI - shot.yaw() * Mth.DEG_TO_RAD, -shot.pitch() * Mth.DEG_TO_RAD, -shot.roll() * Mth.DEG_TO_RAD);
            FORWARDS.rotate(rotation, forwards);
            UP.rotate(rotation, up);
            LEFT.rotate(rotation, left);
        }
        setPosition(shot.position());
    }
}
