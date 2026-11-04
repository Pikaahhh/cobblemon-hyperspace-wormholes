package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.client.render.SnowstormParticle;
import dev.hyperspacewormholes.wormholes.client.BossTransition;
import dev.hyperspacewormholes.wormholes.client.cinematic.Cinematics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No battle weather shows while one of Necrozma's cutscenes shows or a boss screen transition plays: Mega Showdown's
 * weather (a Cobblemon particle storm round the battle, "mega_showdown:weather_*", its particles living up to 25
 * seconds) would otherwise draw a sandstorm through the whole Ultra Burst. Only the drawing of those particles stops,
 * so the weather is back the moment the scene ends.
 */
@Mixin(SnowstormParticle.class)
public abstract class CinematicWeatherMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$noWeatherInCinematic(CallbackInfo ci) {
        if (!Cinematics.isShowing() && !BossTransition.hidesUi()) return;
        ResourceLocation id = ((SnowstormParticle) (Object) this).getStorm().getEffect().getId();
        if (id.getNamespace().equals("mega_showdown") && (id.getPath().startsWith("weather_") || id.getPath().endsWith("sundrop"))) {
            ci.cancel();
        }
    }
}
