package dev.hyperspacewormholes.wormholes.world;

import com.mojang.serialization.MapCodec;
import dev.hyperspacewormholes.wormholes.Wormholes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/** The Ultra Crater's steam vent block. Looks like coarse dirt and puffs out a tall column of smoke. */
public class SteamVent extends BaseEntityBlock {
    private static final MapCodec<SteamVent> CODEC = simpleCodec(SteamVent::new);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Wormholes.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Wormholes.MOD_ID);
    public static final DeferredBlock<SteamVent> BLOCK = BLOCKS.register("steam_vent",
            () -> new SteamVent(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5f).sound(SoundType.GRAVEL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Entity>> ENTITY = BLOCK_ENTITY_TYPES.register("steam_vent",
            () -> BlockEntityType.Builder.of(Entity::new, BLOCK.get()).build(null));

    public SteamVent(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
    }

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
        return level.isClientSide() ? createTickerHelper(type, ENTITY.get(), (world, pos, s, vent) -> puff(world, pos)) : null;
    }

    private static void puff(Level level, BlockPos pos) {
        if (level.getRandom().nextFloat() >= 0.11f || !level.getBlockState(pos.above()).isAir()) return;
        for (int i = 0; i < level.getRandom().nextInt(2) + 2; i++) CampfireBlock.makeParticles(level, pos.above(), true, false);
    }

    public static class Entity extends BlockEntity {
        public Entity(BlockPos pos, BlockState state) {
            super(ENTITY.get(), pos, state);
        }
    }
}
