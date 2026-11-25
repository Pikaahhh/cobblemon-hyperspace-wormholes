package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Things apart (OwnEncounters) aren't among what each other sense nearby.
 */
@Mixin(NearestLivingEntitySensor.class)
public abstract class OwnLivingSensorMixin {
    @ModifyExpressionValue(method = "doTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<LivingEntity> hyperspace_wormholes$without(List<LivingEntity> found, @Local(argsOnly = true) LivingEntity body) {
        return OwnEncounters.without(body, found);
    }
}
