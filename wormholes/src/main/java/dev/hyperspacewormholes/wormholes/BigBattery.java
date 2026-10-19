package dev.hyperspacewormholes.wormholes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * The Big Battery. A steady redstone power source, and if it's right under an Item Analyser you can fire the Analyser
 * from its screen without burning the monitor out.
 */
public final class BigBattery extends Block {
    public static final BigBattery BLOCK = Registry.register(BuiltInRegistries.BLOCK, Wormholes.id("big_battery"),
            new BigBattery(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(1.5f, 6f)
                    .sound(SoundType.METAL)));
    public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, Wormholes.id("big_battery"),
            new BlockItem(BLOCK, new Item.Properties().rarity(Rarity.UNCOMMON)));

    private BigBattery(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 15;
    }
}
