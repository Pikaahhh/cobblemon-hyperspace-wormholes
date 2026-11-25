package dev.hyperspacewormholes.wormholes;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Glowing blocks for the Megalo Tower's outer parts in the Ultra Megalopolis zone: megalo_light, megalo_tread and
 * megalo_shard, setblock-only like NaturalShapes (no item, only from the zone, zones/megalo_281) and registered before
 * it, since its list makes the matching slabs.
 * Each model is vanilla smooth basalt metal with the glowing part as its own elements at full brightness
 * (light_emission 15); the shard is brightened calcite, every face glowing. They give off LIGHT and SHARD_LIGHT.
 */
public final class MegaloLights {
    private static final int LIGHT = 13;
    private static final int SHARD_LIGHT = 10;
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Wormholes.MOD_ID);
    public static final DeferredBlock<Block> LIGHT_BLOCK = block("megalo_light", LIGHT, MapColor.COLOR_CYAN);
    public static final DeferredBlock<Block> TREAD = block("megalo_tread", LIGHT, MapColor.COLOR_CYAN);
    public static final DeferredBlock<Block> SHARD = block("megalo_shard", SHARD_LIGHT, MapColor.SNOW);

    private MegaloLights() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }

    private static DeferredBlock<Block> block(String path, int light, MapColor colour) {
        return BLOCKS.register(path, () -> new Block(
                BlockBehaviour.Properties.of().mapColor(colour).strength(1.5f).sound(SoundType.GLASS)
                .lightLevel(state -> light).isValidSpawn((state, level, pos, type) -> false)));
    }
}
