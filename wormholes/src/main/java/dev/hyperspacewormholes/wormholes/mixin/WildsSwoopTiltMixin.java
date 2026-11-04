package dev.hyperspacewormholes.wormholes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.hyperspacewormholes.wormholes.ride.WildsShadows;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tilts Cliff World's swooping Pokemon nose-down as it dives: while it is still a flying shadow (WildsShadows.isFlyingShadow) its model is turned about its middle by the entity's pitch, which WildsShadows sets along the swoop (0 while circling and once landed). Cobblemon's PokemonRenderer keeps vanilla's setupRotations, so this runs after its turn to face the way it flies. */
@Mixin(LivingEntityRenderer.class)
public abstract class WildsSwoopTiltMixin {
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void hyperspace_wormholes$swoopTilt(LivingEntity entity, PoseStack poseStack, float bob, float yBodyRot,
                                                float partialTick, float scale, CallbackInfo ci) {
        if (!WildsShadows.isFlyingShadow(entity)) return;
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        if (pitch == 0) return;
        float middle = entity.getBbHeight() / 2 / Math.max(scale, 0.01f);
        poseStack.translate(0, middle, 0);
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        poseStack.translate(0, -middle, 0);
    }
}
