package dev.hyperspacewormholes.hyperspace.mixin;

import dev.hyperspacewormholes.hyperspace.IslandEdgeBlock;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops players walking off the edge of an island. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "maybeBackOffFromEdge", at = @At("RETURN"), cancellable = true)
    private void hyperspace_wormholes$stayOnIsland(Vec3 movement, MoverType type, CallbackInfoReturnable<Vec3> cir) {
        if (type != MoverType.SELF && type != MoverType.PLAYER) return;
        cir.setReturnValue(IslandEdgeBlock.clamp((Player) (Object) this, cir.getReturnValue()));
    }
}
