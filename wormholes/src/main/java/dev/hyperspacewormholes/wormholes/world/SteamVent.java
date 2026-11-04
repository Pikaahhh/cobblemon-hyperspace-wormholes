package dev.hyperspacewormholes.wormholes.world;

import com.mojang.serialization.MapCodec;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/** The Ultra Crater's steam vent block. Looks like coarse dirt and puffs out a tall column of smoke. */
public class SteamVent extends BaseEntityBlock {
    private static final MapCodec<SteamVent> CODEC = simpleCodec(SteamVent::new);
    public static final SteamVent BLOCK = Registry.register(BuiltInRegistries.BLOCK, Wormholes.id("steam_vent"),
            new SteamVent(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5f).sound(SoundType.GRAVEL)));
    public static final BlockEntityType<Entity> ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Wormholes.id("steam_vent"), BlockEntityType.Builder.of(Entity::new, BLOCK).build(null));

    public SteamVent(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public static void register() {}

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? createTickerHelper(type, ENTITY, (world, pos, s, vent) -> puff(world, pos)) : null;
    }

    private static void puff(Level level, BlockPos pos) {
        if (level.getRandom().nextFloat() >= 0.11f || !level.getBlockState(pos.above()).isAir()) return;
        for (int i = 0; i < level.getRandom().nextInt(2) + 2; i++) CampfireBlock.makeParticles(level, pos.above(), true, false);
    }

    public static class Entity extends BlockEntity {
        public Entity(BlockPos pos, BlockState state) {
            super(ENTITY, pos, state);
        }
    }
}
