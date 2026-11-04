package dev.hyperspacewormholes.wormholes.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * The structure processors a zone's processor list (worldgen/processor_list) names. Shading is Blends' ladder, dark at the
 * floor to light at the top, from a zone's corner height to 10 over its ceiling (the Ultra Jungle is shaded as if its
 * ceiling were 184). Waterlogging lets blocks that can hold water replace water, and carving clears depth blocks over
 * the top of each zone column so the world's own ground can't cover it.
 */
public final class ZoneProcessors {
    public static final StructureProcessorType<Shading> SHADING = () -> Shading.CODEC;
    public static final StructureProcessorType<Waterlogging> WATERLOGGING = () -> Waterlogging.CODEC;
    public static final StructureProcessorType<Carving> CARVING = () -> Carving.CODEC;

    private ZoneProcessors() {}

    public static final class Shading extends StructureProcessor {
        static final MapCodec<Shading> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("floor").forGetter(p -> p.floor),
                Codec.INT.fieldOf("top").forGetter(p -> p.top)).apply(i, Shading::new));
        private final int floor, top;

        private Shading(int floor, int top) {
            this.floor = floor;
            this.top = top;
        }

        @Override
        public StructureBlockInfo processBlock(LevelReader level, BlockPos origin, BlockPos pivot, StructureBlockInfo raw,
                                               StructureBlockInfo info, StructurePlaceSettings settings) {
            BlockPos pos = info.pos();
            BlockState state = Blends.pick(info.state(), pos.getX(), pos.getY(), pos.getZ(), (pos.getY() - floor) / (double) (top - floor));
            return state == info.state() ? info : new StructureBlockInfo(pos, state, info.nbt());
        }

        @Override
        protected StructureProcessorType<?> getType() {
            return SHADING;
        }
    }

    public static final class Waterlogging extends StructureProcessor {
        static final MapCodec<Waterlogging> CODEC = MapCodec.unit(Waterlogging::new);

        @Override
        public StructureBlockInfo processBlock(LevelReader level, BlockPos origin, BlockPos pivot, StructureBlockInfo raw,
                                               StructureBlockInfo info, StructurePlaceSettings settings) {
            if (!info.state().hasProperty(BlockStateProperties.WATERLOGGED) || !level.getFluidState(info.pos()).is(FluidTags.WATER)) return info;
            return new StructureBlockInfo(info.pos(), info.state().setValue(BlockStateProperties.WATERLOGGED, true), info.nbt());
        }

        @Override
        protected StructureProcessorType<?> getType() {
            return WATERLOGGING;
        }
    }

    public static final class Carving extends StructureProcessor {
        static final MapCodec<Carving> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("zone").forGetter(p -> p.zone),
                Codec.INT.fieldOf("depth").forGetter(p -> p.depth)).apply(i, Carving::new));
        private final String zone;
        private final int depth;

        private Carving(String zone, int depth) {
            this.zone = zone;
            this.depth = depth;
        }

        @Override
        public List<StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos origin, BlockPos pivot, List<StructureBlockInfo> raw,
                                                           List<StructureBlockInfo> processed, StructurePlaceSettings settings) {
            Zone copy = Zone.get(zone).near(origin.getX(), origin.getZ());
            List<StructureBlockInfo> carved = new ArrayList<>(processed);
            for (StructureBlockInfo info : processed) {
                BlockPos pos = info.pos();
                if (copy.columnTop(pos.getX(), pos.getZ()) != pos.getY() + 1) continue;
                for (int up = 1; up <= depth; up++) {
                    BlockPos above = pos.above(up);
                    if (!level.getBlockState(above).isAir()) carved.add(new StructureBlockInfo(above, Blocks.AIR.defaultBlockState(), null));
                }
            }
            return carved;
        }

        @Override
        protected StructureProcessorType<?> getType() {
            return CARVING;
        }
    }
}
