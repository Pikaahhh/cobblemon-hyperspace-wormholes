package dev.hyperspacewormholes.hyperspace.mixin;

import dev.hyperspacewormholes.hyperspace.IslandEdgeBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops ridden Pokemon walking off the edge of an island. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "maybeBackOffFromEdge", at = @At("RETURN"), cancellable = true)
    private void hyperspace_wormholes$keepRidersIn(Vec3 movement, MoverType type, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (type != MoverType.SELF || !(self.getControllingPassenger() instanceof Player rider)) return;
        cir.setReturnValue(IslandEdgeBlock.clamp(self, rider, cir.getReturnValue()));
    }
}
