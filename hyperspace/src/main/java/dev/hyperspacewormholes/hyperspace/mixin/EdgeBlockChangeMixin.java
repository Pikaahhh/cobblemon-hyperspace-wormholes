package dev.hyperspacewormholes.hyperspace.mixin;

import dev.hyperspacewormholes.hyperspace.HyperspaceEdge;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells HyperspaceEdge a block changed on the client, so it traces an island's edge again only when there's something
 * new to trace.
 */
@Mixin(LevelChunk.class)
public abstract class EdgeBlockChangeMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void hyperspace_wormholes$blockChanged(CallbackInfoReturnable<BlockState> cir) {
        if (cir.getReturnValue() != null && ((LevelChunk) (Object) this).getLevel().isClientSide()) HyperspaceEdge.changed();
    }
}
