package dev.hyperspacewormholes.hyperspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

import java.util.List;

/**
 * Loot condition hyperspace_wormholes:hoopa_stars. Passes when your current ring's stars are between a min and max
 * (counts as 5 if you're not on a trip).
 */
public record HoopaStars(int min, int max) implements LootItemCondition {
    public static final int MAX = 5;
    public static final MapCodec<HoopaStars> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("min", 1).forGetter(HoopaStars::min),
            Codec.INT.optionalFieldOf("max", MAX).forGetter(HoopaStars::max)).apply(instance, HoopaStars::new));
    private static final List<String> CANDIES = List.of("xs", "s", "m", "l", "xl");
    private static LootItemConditionType type;

    public static void register() {
        type = Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE,
                ResourceLocation.fromNamespaceAndPath(Hyperspace.MOD_ID, "hoopa_stars"), new LootItemConditionType(CODEC));
    }

    public static int of(ServerPlayer player) {
        WorldData.Trip trip = player == null ? null : WorldData.get(player.server).trips.get(player.getUUID());
        return trip == null ? MAX : Math.min(MAX, Spawns.stars(trip));
    }

    public static Item candy(ServerPlayer player) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse("cobblemon:exp_candy_" + candySize(player)));
    }

    public static String candyName(ServerPlayer player) {
        return "EXP Candy " + candySize(player).toUpperCase();
    }

    private static String candySize(ServerPlayer player) {
        return CANDIES.get(Mth.clamp(of(player), 1, MAX) - 1);
    }

    @Override
    public LootItemConditionType getType() {
        return type;
    }

    @Override
    public boolean test(LootContext context) {
        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        int stars = of(entity instanceof ServerPlayer player ? player : null);
        return stars >= min && stars <= max;
    }
}
