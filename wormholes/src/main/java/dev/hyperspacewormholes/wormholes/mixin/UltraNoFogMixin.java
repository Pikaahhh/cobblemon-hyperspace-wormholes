package dev.hyperspacewormholes.wormholes.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.hyperspacewormholes.wormholes.Wormholes;
import dev.hyperspacewormholes.wormholes.client.SkyClouds;
import dev.hyperspacewormholes.wormholes.ride.UltraDimensions;
import dev.hyperspacewormholes.wormholes.ride.UltraZones;
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
 * No distance fog in our Ultra Space dimensions, except the zones, and closer fog over Cliff World so the edge of its
 * sea of clouds (SkyClouds) fades away. No water fog in the Ultra Deep Sea either; other water, lava, powder snow,
 * Blindness and Darkness fog still work.
 * The Ultra Plant gets the game's blue haze, from HAZE_START out to HAZE_REACH times the view distance, so the far edge
 * is about 60% hazed into the fog colour (the biome's blue, which UltraPlantSky's horizon meets).
 */
@Mixin(FogRenderer.class)
public abstract class UltraNoFogMixin {
    private static final float HAZE_START = 44, HAZE_REACH = 1.6f;

    @Inject(method = "setupFog", at = @At("RETURN"), require = 0)
    private static void hyperspace_wormholes$noUltraFog(Camera camera, FogRenderer.FogMode mode, float renderDistance,
                                                     boolean foggy, float partialTick, CallbackInfo ci) {
        if (mode != FogRenderer.FogMode.FOG_TERRAIN || !UltraDimensions.isUltra(camera.getEntity().level())) return;
        if (camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) return;
        if (camera.getFluidInCamera() == FogType.WATER && camera.getEntity().level().dimension() == Wormholes.ULTRA_DEEP_SEA) {
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
            return;
        }
        if (camera.getFluidInCamera() != FogType.NONE) return;
        if (UltraZones.isSky(camera.getEntity().level())) {
            RenderSystem.setShaderFogStart(SkyClouds.FOG_START);
            RenderSystem.setShaderFogEnd(SkyClouds.FOG_END);
            return;
        }
        if (camera.getEntity().level().dimension() == Wormholes.ULTRA_PLANT) {
            RenderSystem.setShaderFogStart(HAZE_START);
            RenderSystem.setShaderFogEnd(Math.max(renderDistance * HAZE_REACH, HAZE_START * 2));
            return;
        }
        if (UltraZones.isZone(camera.getEntity().level())) return;
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
    }
}
