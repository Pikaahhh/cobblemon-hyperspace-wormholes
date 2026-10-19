package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The Ultra Ruins' dead trees: bare basalt trunks with a few stubby branches.
 */
public class DeadTreeFeature extends Feature<NoneFeatureConfiguration> {
    public DeadTreeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos foot = context.origin();
        BlockState ground = level.getBlockState(foot.below());
        if (!ground.is(Blocks.DIRT) && !ground.is(Blocks.COARSE_DIRT) && !ground.is(Blocks.SAND)) return false;
        int height = 4 + random.nextInt(5);
        for (int y = 0; y < height; y++) {
            if (!level.isEmptyBlock(foot.above(y))) return y > 0;
            level.setBlock(foot.above(y), log(Direction.Axis.Y), Block.UPDATE_CLIENTS);
        }
        int branches = 1 + random.nextInt(3);
        for (int i = 0; i < branches; i++) {
            Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            BlockPos at = foot.above(height - 1 - random.nextInt(Math.max(1, height / 2)));
            int length = 1 + random.nextInt(3);
            for (int j = 1; j <= length; j++) {
                at = at.relative(side);
                if (j == length && random.nextBoolean()) at = at.above();
                if (!level.isEmptyBlock(at)) break;
                level.setBlock(at, log(side.getAxis()), Block.UPDATE_CLIENTS);
            }
        }
        return true;
    }

    private static BlockState log(Direction.Axis axis) {
        return Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }
}
