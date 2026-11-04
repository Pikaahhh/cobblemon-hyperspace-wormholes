package dev.hyperspacewormholes.wormholes;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

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
    public static final Block LIGHT_BLOCK = block("megalo_light", LIGHT, MapColor.COLOR_CYAN);
    public static final Block TREAD = block("megalo_tread", LIGHT, MapColor.COLOR_CYAN);
    public static final Block SHARD = block("megalo_shard", SHARD_LIGHT, MapColor.SNOW);

    private MegaloLights() {}

    public static void register() {}

    private static Block block(String path, int light, MapColor colour) {
        return Registry.register(BuiltInRegistries.BLOCK, Wormholes.id(path), new Block(
                BlockBehaviour.Properties.of().mapColor(colour).strength(1.5f).sound(SoundType.GLASS)
                .lightLevel(state -> light).isValidSpawn((state, level, pos, type) -> false)));
    }
}
