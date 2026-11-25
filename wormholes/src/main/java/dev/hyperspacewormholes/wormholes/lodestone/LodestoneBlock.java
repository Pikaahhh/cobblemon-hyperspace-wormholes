package dev.hyperspacewormholes.wormholes.lodestone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Vanilla's lodestone (LodestoneBlockMixin), with a block entity to hold its name.
 */
public class LodestoneBlock extends Block implements EntityBlock {
    public LodestoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LodestoneBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            LodestoneIndex.of(server.getServer()).remove(GlobalPos.of(level.dimension(), pos));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
