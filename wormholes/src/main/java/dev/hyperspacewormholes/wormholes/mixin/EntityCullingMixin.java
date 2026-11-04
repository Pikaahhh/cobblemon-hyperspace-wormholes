package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.hyperspacewormholes.wormholes.client.RiderArrivalClient;
import dev.hyperspacewormholes.wormholes.client.cinematic.UltraBurstCinematic;
import dev.hyperspacewormholes.wormholes.ride.StaticPokemon;
import dev.hyperspacewormholes.wormholes.ride.UltraDimensions;
import dev.hyperspacewormholes.wormholes.ride.UltraZones;
import dev.hyperspacewormholes.wormholes.ride.WildsShadows;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Never culls a rider coming out of a wormhole (drawn at the mouth, away from its hitbox), the Ultra Burst Necrozma
 * (its core rises far out of its hitbox), a Wilds shadow in a zone, another player in the mod's worlds
 * (UltraDimensions.isUltra) or one of the mod's still Pokemon (StaticPokemon), by having their renderers treat them as
 * noCulling.
 * That skips the game's frustum test and the culling mods' hooks; the distance check still applies.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityCullingMixin {
    @ModifyExpressionValue(method = "shouldRender", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/Entity;noCulling:Z"))
    private boolean hyperspace_wormholes$neverCull(boolean noCulling, @Local(argsOnly = true) Entity entity) {
        return noCulling || RiderArrivalClient.isEmerging(entity) || UltraBurstCinematic.isBoss(entity)
                || WildsShadows.isShadow(entity) && UltraZones.isZone(entity.level())
                || entity instanceof RemotePlayer && UltraDimensions.isUltra(entity.level())
                || StaticPokemon.is(entity);
    }
}
