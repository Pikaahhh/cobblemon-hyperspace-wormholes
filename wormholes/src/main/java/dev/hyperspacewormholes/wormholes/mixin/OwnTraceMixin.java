package dev.hyperspacewormholes.wormholes.mixin;

import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Cobblemon's own look traces (battles, spectating, interacting) go through another player's own Pokemon (OwnEncounters).
 */
@Mixin(value = PlayerExtensionsKt.class, remap = false)
public abstract class OwnTraceMixin {
    @ModifyExpressionValue(method = "traceEntityCollision*", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private static List<Entity> hyperspace_wormholes$without(List<Entity> found, @Local(argsOnly = true) LivingEntity viewer) {
        return OwnEncounters.without(viewer, found);
    }
}
