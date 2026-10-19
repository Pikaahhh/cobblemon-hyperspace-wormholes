package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.hyperspacewormholes.wormholes.client.RideClient;
import dev.hyperspacewormholes.wormholes.client.RiderArrivalClient;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the third-person camera behind and a bit above the legend during the ride, steady against its flying animation.
 * Dusk Mane and Dawn Wings Necrozma get it a bit higher and further back (4 up and 13 back instead of 2.2 and 11).
 */
@Mixin(Camera.class)
public abstract class CameraZoomMixin {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract Vector3f getLookVector();

    @Shadow
    public abstract Vector3f getUpVector();

    @Inject(method = "setup", at = @At("TAIL"))
    private void hyperspace_wormholes$necrozmaView(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                               float partialTick, CallbackInfo ci) {
        if (detached || entity == null || !(entity.getVehicle() instanceof PokemonEntity mount)) return;
        if (!mount.getPokemon().getSpecies().getResourceIdentifier().getPath().equals("necrozma")) return;
        if (mount.getPokemon().getAspects().contains("dusk-fusion") || mount.getPokemon().getAspects().contains("dawn-fusion")) return;
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
        setRotation(0f, 0f);
        Entity body = entity.getVehicle() != null ? entity.getVehicle() : entity;
        Vec3 pivot = body.getPosition(partialTick).add(0, body.getBbHeight() * 0.6, 0);
        boolean fusion = body instanceof PokemonEntity mount
                && (mount.getPokemon().getAspects().contains("dusk-fusion") || mount.getPokemon().getAspects().contains("dawn-fusion"));
        float distance = fusion ? 13f : 11f;
        float lift = fusion ? 4f : 2.2f;
        Vector3f look = getLookVector();
        Vector3f up = getUpVector();
        setPosition(pivot.add(
                -look.x * distance + up.x * lift,
                -look.y * distance + up.y * lift,
                -look.z * distance + up.z * lift));
    }
}
