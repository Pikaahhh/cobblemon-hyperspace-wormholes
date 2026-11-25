package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.client.RideCamera;
import dev.hyperspacewormholes.wormholes.client.RideClient;
import dev.hyperspacewormholes.wormholes.client.RiderArrivalClient;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

/**
 * Puts the third-person camera where RideCamera says during the ride.
 */
@Mixin(Camera.class)
public abstract class CameraZoomMixin {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void hyperspace_wormholes$necrozmaView(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                               float partialTick, CallbackInfo ci) {
        if (detached || entity == null || !(entity.getVehicle() instanceof PokemonEntity mount)) return;
        if (!mount.getPokemon().getSpecies().getResourceIdentifier().getPath().equals("necrozma") || RideCamera.fused(mount)) return;
        float yaw = Mth.rotLerp(partialTick, mount.yBodyRotO, mount.yBodyRot) * Mth.DEG_TO_RAD;
        double ahead = mount.getBbWidth() / 2 + 0.6;
        setPosition(mount.getEyePosition(partialTick).add(-Mth.sin(yaw) * ahead, 0, Mth.cos(yaw) * ahead));
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void hyperspace_wormholes$emergenceCamera(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                                   float partialTick, CallbackInfo ci) {
        double[] view = RiderArrivalClient.camera(partialTick);
        if (view == null) return;
        setRotation((float) view[3], (float) view[4]);
        setPosition(new Vec3(view[0], view[1], view[2]));
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void hyperspace_wormholes$rideCamera(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                              float partialTick, CallbackInfo ci) {
        if (!detached || !RideClient.isActive() || entity == null) return;
        RideCamera.View view = RideCamera.view(Objects.requireNonNullElse(entity.getVehicle(), entity), partialTick);
        setRotation(view.yaw(), view.pitch());
        setPosition(view.eye());
    }
}
