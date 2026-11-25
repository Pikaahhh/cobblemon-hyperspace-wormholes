package dev.hyperspacewormholes.wormholes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The Ultra Deep Sea's oil-slick crystals: blue ice's hardness, slipperiness and sound (it drops itself only with Silk
 * Touch), but translucent like ice and glowing at LIGHT, which fades out 4 blocks away. Its texture is vanilla ice in
 * greyscale, tinted by height through WormholesClient's colour providers: GRADIENT runs up over RUN blocks from FROM_Y
 * and back down over the next RUN. It also grows in lumps out of cave walls, floors and roofs under tundra biomes
 * (world/LuminescentIceFeature), and registers before NaturalShapes, which makes its shapes from it.
 */
public final class LuminescentIce {
    private static final int LIGHT = 5;
    private static final int FROM_Y = 64;
    private static final int RUN = 8;
    private static final int[] GRADIENT = {0xB3DAA6, 0xAFE6A7, 0xA8E2AB, 0x9DE2AF, 0x8EE3B0, 0x7EDBB3, 0x65D2B6,
            0x4FCCC0, 0x49D5DF, 0x4BC4ED, 0x49A2E2, 0x508CDD, 0x6B8AE1, 0x7D8AE7, 0x9491E4, 0xA177BC, 0xA682B3, 0xAE85B6,
            0xAB8AAB, 0xB390AB};
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Wormholes.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Wormholes.MOD_ID);
    public static final DeferredBlock<Block> BLOCK = BLOCKS.register("luminescent_ice",
            () -> new HalfTransparentBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(2.8f).friction(0.989f)
                    .sound(SoundType.GLASS).lightLevel(state -> LIGHT).noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false).isRedstoneConductor((state, level, pos) -> false)));
    public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem(BLOCK);

    private LuminescentIce() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    public static List<Block> family() {
        return List.of(BLOCK.get(), shape("slab"), shape("stairs"));
    }

    private static Block shape(String kind) {
        return BuiltInRegistries.BLOCK.get(Wormholes.id("luminescent_ice_" + kind));
    }

    public static int colour(float y) {
        float p = Mth.positiveModulo(y - FROM_Y, 2 * RUN) / (float) RUN;
        float at = (p <= 1 ? p : 2 - p) * (GRADIENT.length - 1);
        int i = Math.min((int) at, GRADIENT.length - 2);
        return lerp(GRADIENT[i], GRADIENT[i + 1], at - i);
    }

    private static int lerp(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, a >> 16 & 255, b >> 16 & 255));
        int g = Math.round(Mth.lerp(t, a >> 8 & 255, b >> 8 & 255));
        int bl = Math.round(Mth.lerp(t, a & 255, b & 255));
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }
}
