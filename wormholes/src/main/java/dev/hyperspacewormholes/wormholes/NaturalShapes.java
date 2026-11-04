package dev.hyperspacewormholes.wormholes;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Setblock-only slabs, stairs, walls and fences of natural blocks that have none in vanilla, for the Ultra Beast
 * worlds' fitted shapes.
 * Each line of data/hyperspace_wormholes/natural_shapes.txt is a kind and a base block ("slab minecraft:calcite"),
 * registered as hyperspace_wormholes:&lt;base path&gt;_&lt;kind&gt; with the base block's behaviour and no item. Map
 * colour and light come from the base block's default state, since a copied per-state rule can read a property the new
 * shape doesn't have (a log's axis).
 */
public final class NaturalShapes {
    private static final String LIST = "/data/hyperspace_wormholes/natural_shapes.txt";

    private NaturalShapes() {}

    public static void register() {
        for (String line : lines()) {
            String[] parts = line.trim().split("\\s+");
            ResourceLocation baseId = ResourceLocation.parse(parts[1]);
            Block base = BuiltInRegistries.BLOCK.get(baseId);
            int light = base.defaultBlockState().getLightEmission();
            BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(base)
                    .mapColor(base.defaultMapColor()).lightLevel(state -> light);
            Block block = switch (parts[0]) {
                case "slab" -> new SlabBlock(properties);
                case "stairs" -> new StairBlock(base.defaultBlockState(), properties);
                case "wall" -> new WallBlock(properties.forceSolidOn());
                case "fence" -> new FenceBlock(properties);
                default -> throw new IllegalStateException("unknown shape in " + LIST + ": " + line);
            };
            Registry.register(BuiltInRegistries.BLOCK, Wormholes.id(baseId.getPath() + "_" + parts[0]), block);
        }
    }

    private static List<String> lines() {
        try (InputStream raw = NaturalShapes.class.getResourceAsStream(LIST)) {
            if (raw == null) throw new IOException("missing " + LIST);
            return new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8)).lines()
                    .filter(l -> !l.isBlank()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
