package dev.hyperspacewormholes.hyperspace.mixin;

import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Lets the structure block screen's size and offset go up to 127 per axis instead of 48 when the server reads them.
 * StructureBlockSizeMixin does the block side.
 */
@Mixin(ServerboundSetStructureBlockPacket.class)
public class StructureBlockPacketMixin {
    @ModifyConstant(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", constant = @Constant(intValue = 48), require = 0)
    private int hyperspace_wormholes$maxSize(int old) {
        return 127;
    }

    @ModifyConstant(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", constant = @Constant(intValue = -48), require = 0)
    private int hyperspace_wormholes$minOffset(int old) {
        return -127;
    }
}
