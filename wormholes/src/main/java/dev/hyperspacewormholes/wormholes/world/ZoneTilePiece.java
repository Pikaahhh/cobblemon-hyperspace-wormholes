package dev.hyperspacewormholes.wormholes.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * One template tile of a zone (ZoneStructure), placed through vanilla's StructureTemplate. It saves only the tile's and processor list's names, and reads the template when a chunk is built, not when the chunk holding the start loads (TemplateStructurePiece would, on every load, for all of a zone's tiles).
 * Each chunk gets its own settings, as neighbouring chunks can build at once: the shape and water are as the template and the processors say (no edge updates, no fluid ticks), and IN_CHUNK comes first since vanilla runs the processors over a whole tile before it clips, and they may only read the chunk being built.
 */
public class ZoneTilePiece extends StructurePiece {
    public static final StructurePieceType TYPE = (context, tag) -> new ZoneTilePiece(tag);
    private static final StructureProcessor IN_CHUNK = new StructureProcessor() {
        @Override
        public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos origin, BlockPos pivot,
                StructureTemplate.StructureBlockInfo raw, StructureTemplate.StructureBlockInfo info, StructurePlaceSettings settings) {
            return settings.getBoundingBox().isInside(info.pos()) ? info : null;
        }

        @Override
        protected StructureProcessorType<?> getType() {
            return null;
        }
    };

    private final ResourceLocation template, processors;

    ZoneTilePiece(ResourceLocation template, ResourceLocation processors, BoundingBox box) {
        super(TYPE, 0, box);
        this.template = template;
        this.processors = processors;
    }

    private ZoneTilePiece(CompoundTag tag) {
        super(TYPE, tag);
        template = ResourceLocation.parse(tag.getString("Template"));
        processors = ResourceLocation.parse(tag.getString("Processors"));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("Template", template.toString());
        tag.putString("Processors", processors.toString());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        place(level, level.getLevel().getStructureManager().getOrCreate(template),
                level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).getOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, processors)),
                box, pivot, random);
    }

    void place(WorldGenLevel level, StructureTemplate tile, StructureProcessorList list, BoundingBox box, BlockPos pivot, RandomSource random) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(true)
                .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING).setBoundingBox(box).addProcessor(IN_CHUNK);
        list.list().forEach(settings::addProcessor);
        tile.placeInWorld(level, new BlockPos(boundingBox.minX(), boundingBox.minY(), boundingBox.minZ()), pivot, settings, random, Block.UPDATE_CLIENTS);
    }
}
