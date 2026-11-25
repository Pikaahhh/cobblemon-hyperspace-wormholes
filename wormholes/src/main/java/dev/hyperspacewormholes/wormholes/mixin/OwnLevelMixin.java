package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.ride.OwnEncounters;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Leaves another player's own Pokemon out of an entity's searches round it, and everyone but its player's side out of that
 * Pokemon's (OwnEncounters.apart): what it can collide with, push, hit, pick, pick up, ride or sweep.
 */
@Mixin(Level.class)
public abstract class OwnLevelMixin {
    @Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private void hyperspace_wormholes$without(Entity viewer, AABB box, Predicate<? super Entity> selector,
                                              CallbackInfoReturnable<List<Entity>> cir) {
        List<Entity> found = cir.getReturnValue();
        List<Entity> kept = OwnEncounters.without(viewer, found);
        if (kept != found) cir.setReturnValue(kept);
    }
}
