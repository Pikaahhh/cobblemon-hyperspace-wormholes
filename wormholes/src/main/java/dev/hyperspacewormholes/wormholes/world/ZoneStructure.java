package dev.hyperspacewormholes.wormholes.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A main zone as a data-driven structure (worldgen/structure, type hyperspace_wormholes:zone): one ZoneTilePiece per
 * template tile of the zone, at the copy nearest its start, unturned, with its processor list. It takes the place of
 * minecraft:jigsaw, which turns its start at random. A start only reaches 8 chunks, so the Ultra Crater and Ultra Desert
 * are two structures each, split by tiles, each with its own structure set (ZoneSpotPlacement). The no_op placed feature
 * zone_slot holds the first zone's index, since a feature's seed comes from its index (Cobblemon's come after it).
 */
public class ZoneStructure extends Structure {
    public static final MapCodec<ZoneStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(settingsCodec(i),
            Codec.STRING.fieldOf("zone").forGetter(s -> s.zone),
            StructureProcessorType.LIST_CODEC.fieldOf("processors").forGetter(s -> s.processors),
            Codec.INT.listOf().optionalFieldOf("tiles", List.of(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE)).forGetter(s -> s.tiles))
            .apply(i, ZoneStructure::new));
    public static final StructureType<ZoneStructure> TYPE = () -> CODEC;
    private static final String FOLDER = "zones";

    private final String zone;
    private final Holder<StructureProcessorList> processors;
    private final List<Integer> tiles;

    private ZoneStructure(StructureSettings settings, String zone, Holder<StructureProcessorList> processors, List<Integer> tiles) {
        super(settings);
        this.zone = zone;
        this.processors = processors;
        this.tiles = tiles;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        List<ZoneTilePiece> pieces = pieces(chunk);
        if (pieces.isEmpty()) return Optional.empty();
        BlockPos at = new BlockPos(chunk.getMiddleBlockX(), pieces.get(0).getBoundingBox().minY(), chunk.getMiddleBlockZ());
        return Optional.of(new GenerationStub(at, builder -> pieces.forEach(builder::addPiece)));
    }

    List<ZoneTilePiece> pieces(ChunkPos chunk) {
        try {
            Zone home = Zone.get(zone);
            BlockPos copy = home.near(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()).arrival().subtract(home.arrival());
            CompoundTag data = TemplateTiles.data(FOLDER, zone);
            int[] all = data.getIntArray("tiles"), corner = data.getIntArray("corner"), size = data.getIntArray("size"), shift = data.getIntArray("shift");
            int x0 = corner[0] - shift[0] + copy.getX(), y0 = corner[1] + data.getInt("bottom"), z0 = corner[2] - shift[1] + copy.getZ();
            int wide = size[0] + shift[0], high = data.getInt("height"), deep = size[2] + shift[1];
            List<ZoneTilePiece> pieces = new ArrayList<>();
            for (int i = 0; i < all.length; i += 3) {
                int tx = all[i], ty = all[i + 1], tz = all[i + 2];
                if (tx < tiles.get(0) || tz < tiles.get(1) || tx > tiles.get(2) || tz > tiles.get(3)) continue;
                int x = x0 + tx * TemplateTiles.SIDE, y = y0 + ty * TemplateTiles.SIDE, z = z0 + tz * TemplateTiles.SIDE;
                pieces.add(new ZoneTilePiece(ResourceLocation.fromNamespaceAndPath(Wormholes.MOD_ID, TemplateTiles.tile(FOLDER, zone, data, i)),
                        processors.unwrapKey().orElseThrow().location(),
                        new BoundingBox(x, y, z, x + Math.min(TemplateTiles.SIDE, wide - tx * TemplateTiles.SIDE) - 1,
                                y + Math.min(TemplateTiles.SIDE, high - ty * TemplateTiles.SIDE) - 1,
                                z + Math.min(TemplateTiles.SIDE, deep - tz * TemplateTiles.SIDE) - 1)));
            }
            return pieces;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public StructureType<?> type() {
        return TYPE;
    }
}
