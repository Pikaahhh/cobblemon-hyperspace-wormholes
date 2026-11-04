package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.hyperspacewormholes.wormholes.ride.StaticPokemon;
import dev.hyperspacewormholes.wormholes.ride.UltraDimensions;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Draws every other player within entity render distance in any of the mod's worlds (UltraDimensions.isUltra), even where
 * the game thinks their section isn't built yet; in an empty world built round a zone that check can lag or miss the
 * section another player arrives in. The mod's still Pokemon (StaticPokemon) are drawn the same way, with
 * mixin/EntityCullingMixin keeping the frustum test and culling mods off them. Anything a Necrozma cutscene hides
 * (mixin/CinematicHideMixin) never gets this far.
 */
@Mixin(LevelRenderer.class)
public abstract class ZonePlayerVisibleMixin {
    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiled(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean hyperspace_wormholes$zonePlayers(boolean compiled, @Local Entity entity) {
        return compiled || entity instanceof RemotePlayer && UltraDimensions.isUltra(entity.level()) || StaticPokemon.is(entity);
    }
}
