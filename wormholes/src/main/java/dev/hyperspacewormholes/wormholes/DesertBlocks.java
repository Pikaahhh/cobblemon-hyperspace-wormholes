package dev.hyperspacewormholes.wormholes;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Function;

/**
 * The Ultra Desert's own blocks: white sand (falls like sand) and white sandstone, the zone's sand and trench walls.
 * Setblock-only like MegaloLights and NaturalShapes: no item, recipe or creative tab entry, and nothing dropped when broken; they only come from the Ultra Desert's generation (zones/desert_284*, UltraDesertSandFeature) and /setblock.
 */
public final class DesertBlocks {
    public static final Block WHITE_SAND = block("white_sand", p -> new ColoredFallingBlock(new ColorRGBA(0xE4DFD3), p),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SAND).mapColor(MapColor.SNOW));
    public static final Block WHITE_SANDSTONE = block("white_sandstone", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SANDSTONE).mapColor(MapColor.SNOW));

    private DesertBlocks() {}

    public static void register() {}

    private static Block block(String path, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        return Registry.register(BuiltInRegistries.BLOCK, Wormholes.id(path), factory.apply(properties));
    }
}
