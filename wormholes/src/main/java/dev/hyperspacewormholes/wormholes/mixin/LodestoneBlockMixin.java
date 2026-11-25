package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.hyperspacewormholes.wormholes.lodestone.LodestoneBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Makes vanilla's lodestone a LodestoneBlock, so it can hold a name. Its id and properties stay vanilla's. Wrapped, not
 * redirected, so another mod can hook the same place. The original isn't called: a block made and never registered crashes
 * the game.
 */
@Mixin(Blocks.class)
public class LodestoneBlockMixin {
    @WrapOperation(method = "<clinit>", at = @At(value = "NEW", ordinal = 0,
            target = "(Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)Lnet/minecraft/world/level/block/Block;"),
            slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=lodestone")))
    private static Block hyperspace_wormholes$lodestone(BlockBehaviour.Properties properties, Operation<Block> original) {
        return new LodestoneBlock(properties);
    }
}
