package dev.hyperspacewormholes.hyperspace.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access to setting a block display's block, for the light column above spatial distortions. */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayAccessor {
    @Invoker("setBlockState")
    void hyperspace_wormholes$setBlockState(BlockState state);
}
