package dev.hyperspacewormholes.hyperspace.mixin;

import net.minecraft.world.level.block.entity.StructureBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Bumps the structure block's size and offset limit from 48 to 127 per axis (127 is the most the vanilla packet can
 * send, it's one byte each), and lets Detect find corners that far out (was 80). StructureBlockPacketMixin does the
 * screen side.
 */
@Mixin(StructureBlockEntity.class)
public class StructureBlockSizeMixin {
    @ModifyConstant(method = "loadAdditional", constant = @Constant(intValue = 48), require = 0)
    private int hyperspace_wormholes$maxSize(int old) {
        return 127;
    }

    @ModifyConstant(method = "loadAdditional", constant = @Constant(intValue = -48), require = 0)
    private int hyperspace_wormholes$minOffset(int old) {
        return -127;
    }

    @ModifyConstant(method = "detectSize", constant = @Constant(intValue = 80), require = 0)
    private int hyperspace_wormholes$searchRange(int old) {
        return 127;
    }
}
