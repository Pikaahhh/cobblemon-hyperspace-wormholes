package dev.hyperspacewormholes.wormholes.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.hyperspacewormholes.wormholes.ride.UltraDimensions;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No distance fog in our Ultra Space dimensions. Water, lava, powder snow, Blindness and Darkness fog still work.
 */
@Mixin(FogRenderer.class)
public abstract class UltraNoFogMixin {
    @Inject(method = "setupFog", at = @At("RETURN"), require = 0)
    private static void hyperspace_wormholes$noUltraFog(Camera camera, FogRenderer.FogMode mode, float renderDistance,
                                                     boolean foggy, float partialTick, CallbackInfo ci) {
        if (mode != FogRenderer.FogMode.FOG_TERRAIN || camera.getFluidInCamera() != FogType.NONE) return;
        if (!UltraDimensions.isUltra(camera.getEntity().level())) return;
        if (camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) return;
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
    }
}
