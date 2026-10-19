package dev.hyperspacewormholes.hyperspace;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Loot function hyperspace_wormholes:hoopa_stars_count. Scales item counts by your ring's stars, from a fifth at 1
 * star up to the full amount at 5. Rounds randomly and never goes below 1.
 */
public record HoopaStarsCount() implements LootItemFunction {
    public static final MapCodec<HoopaStarsCount> CODEC = MapCodec.unit(new HoopaStarsCount());
    private static LootItemFunctionType<HoopaStarsCount> type;

    public static void register() {
        type = Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE,
                ResourceLocation.fromNamespaceAndPath(Hyperspace.MOD_ID, "hoopa_stars_count"), new LootItemFunctionType<>(CODEC));
    }

    public static float factor(int stars) {
        return Mth.clamp(stars, 1, HoopaStars.MAX) / (float) HoopaStars.MAX;
    }

    @Override
    public LootItemFunctionType<HoopaStarsCount> getType() {
        return type;
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext context) {
        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        float scaled = stack.getCount() * factor(HoopaStars.of(entity instanceof ServerPlayer player ? player : null));
        int count = Mth.floor(scaled) + (context.getRandom().nextFloat() < scaled - Mth.floor(scaled) ? 1 : 0);
        stack.setCount(Math.max(1, count));
        return stack;
    }
}
