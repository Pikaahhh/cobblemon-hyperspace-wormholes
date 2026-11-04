package dev.hyperspacewormholes.wormholes.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

import java.util.Optional;

/**
 * A structure set's placement (hyperspace_wormholes:zone_spot) for a start at one fixed chunk and, given a repeat in
 * chunks, at the same spot every repeat chunks on both axes (625 chunks is the 10,000 blocks Zone repeats at). Vanilla's
 * random_spread can make a grid but only from chunk 0, and nothing in vanilla makes a single spot.
 */
public class ZoneSpotPlacement extends StructurePlacement {
    public static final MapCodec<ZoneSpotPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> placementCodec(i).and(i.group(
            Codec.INT.fieldOf("chunk_x").forGetter(p -> p.chunkX),
            Codec.INT.fieldOf("chunk_z").forGetter(p -> p.chunkZ),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("repeat", 0).forGetter(p -> p.repeat))).apply(i, ZoneSpotPlacement::new));
    public static final StructurePlacementType<ZoneSpotPlacement> TYPE = () -> CODEC;

    private final int chunkX, chunkZ, repeat;

    private ZoneSpotPlacement(Vec3i locateOffset, FrequencyReductionMethod reduction, float frequency, int salt,
                              Optional<ExclusionZone> exclusion, int chunkX, int chunkZ, int repeat) {
        super(locateOffset, reduction, frequency, salt, exclusion);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.repeat = repeat;
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int x, int z) {
        return repeat == 0 ? x == chunkX && z == chunkZ
                : Mth.positiveModulo(x - chunkX, repeat) == 0 && Mth.positiveModulo(z - chunkZ, repeat) == 0;
    }

    @Override
    public StructurePlacementType<?> type() {
        return TYPE;
    }
}
